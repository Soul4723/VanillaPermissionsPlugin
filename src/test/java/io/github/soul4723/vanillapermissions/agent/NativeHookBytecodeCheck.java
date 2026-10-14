package io.github.soul4723.vanillapermissions.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Checks native hook targets against a remapped Paper server jar. */
public final class NativeHookBytecodeCheck {
    private static final String PERMISSION_BRIDGE =
        "io/github/soul4723/vanillapermissions/agent/PermissionBridge";
    private static final String SELECTOR_BRIDGE =
        "io/github/soul4723/vanillapermissions/agent/SelectorBridge";

    private NativeHookBytecodeCheck() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass path to remapped Paper server jar");
        try (JarFile paper = new JarFile(Path.of(args[0]).toFile())) {
            check(paper, "net/minecraft/server/dedicated/DedicatedServer",
                new PermissionCallSiteHook("spawn", "net/minecraft/server/dedicated/DedicatedServer",
                    "isUnderSpawnProtection",
                    "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z",
                    "net/minecraft/server/dedicated/DedicatedPlayerList", "isOp",
                    "(Lcom/mojang/authlib/GameProfile;)Z", "minecraft.bypass.spawn-protection", 3),
                PERMISSION_BRIDGE, "check", 1);
            check(paper, "net/minecraft/commands/arguments/selector/EntitySelector",
                new PermissionCallSiteHook("selector", "net/minecraft/commands/arguments/selector/EntitySelector",
                    "checkPermissions", "(Lnet/minecraft/commands/CommandSourceStack;)V",
                    "net/minecraft/commands/CommandSourceStack", "hasPermission", "(ILjava/lang/String;)Z",
                    "minecraft.selector", 1), PERMISSION_BRIDGE, "check", 1);
            check(paper, "net/minecraft/commands/arguments/EntityArgument",
                new SelectorResultHook("entity", "net/minecraft/commands/arguments/EntityArgument",
                    Set.of("getEntity", "getPlayer", "getEntities", "getOptionalEntities", "getPlayers", "getOptionalPlayers"), null),
                SELECTOR_BRIDGE, "validate", 6);
            check(paper, "net/minecraft/commands/arguments/GameProfileArgument",
                new SelectorResultHook("profile", "net/minecraft/commands/arguments/GameProfileArgument",
                    Set.of("getGameProfiles"),
                    "(Lcom/mojang/brigadier/context/CommandContext;Ljava/lang/String;)Ljava/util/Collection;"),
                SELECTOR_BRIDGE, "validate", 1);
            check(paper, "net/minecraft/commands/arguments/ScoreHolderArgument",
                new SelectorResultHook("score-holder", "net/minecraft/commands/arguments/ScoreHolderArgument",
                    Set.of("getNames"),
                    "(Lcom/mojang/brigadier/context/CommandContext;Ljava/lang/String;Ljava/util/function/Supplier;)Ljava/util/Collection;"),
                SELECTOR_BRIDGE, "validate", 1);
        }
    }

    private static void check(JarFile paper, String className, NmsFeatureHook hook,
                              String bridge, String method, int expectedCalls) throws Exception {
        byte[] source;
        try (InputStream input = paper.getInputStream(new JarEntry(className + ".class"))) {
            source = input.readAllBytes();
        }
        byte[] transformed = hook.transform(source);
        int[] calls = {0};
        new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC && owner.equals(bridge) && name.equals(method)) calls[0]++;
                    }
                };
            }
        }, 0);
        if (calls[0] != expectedCalls) {
            throw new AssertionError(hook.id() + ": expected " + expectedCalls + " bridge calls, got " + calls[0]);
        }
    }
}

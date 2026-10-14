package io.github.soul4723.vanillapermissions.agent;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public final class VanillaPermissionsAgent {
    private static final String[] BRIDGE_CLASSES = {
        "io/github/soul4723/vanillapermissions/agent/PermissionBridge.class",
        "io/github/soul4723/vanillapermissions/agent/PermissionBridge$PermissionCheck.class",
        "io/github/soul4723/vanillapermissions/agent/SelectorBridge.class",
        "io/github/soul4723/vanillapermissions/agent/SelectorBridge$SelectorCheck.class"
    };
    private static JarFile bridgeJar;

    private VanillaPermissionsAgent() {}

    public static void premain(String arguments, Instrumentation instrumentation) {
        installBootstrapBridge(instrumentation);
        List<NmsFeatureHook> hooks = List.of(
            new PermissionCallSiteHook(
                "bypass.spawn-protection",
                "net/minecraft/server/dedicated/DedicatedServer",
                "isUnderSpawnProtection",
                "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z",
                "net/minecraft/server/dedicated/DedicatedPlayerList",
                "isOp",
                "(Lcom/mojang/authlib/GameProfile;)Z",
                "minecraft.bypass.spawn-protection",
                3),
            new PermissionCallSiteHook(
                "selector.permission",
                "net/minecraft/commands/arguments/selector/EntitySelector",
                "checkPermissions",
                "(Lnet/minecraft/commands/CommandSourceStack;)V",
                "net/minecraft/commands/CommandSourceStack",
                "hasPermission",
                "(ILjava/lang/String;)Z",
                "minecraft.selector",
                1),
            new SelectorResultHook(
                "selector.entity-arguments",
                "net/minecraft/commands/arguments/EntityArgument",
                Set.of("getEntity", "getPlayer", "getEntities", "getOptionalEntities", "getPlayers", "getOptionalPlayers"),
                null),
            new SelectorResultHook(
                "selector.profile-arguments",
                "net/minecraft/commands/arguments/GameProfileArgument",
                Set.of("getGameProfiles"),
                "(Lcom/mojang/brigadier/context/CommandContext;Ljava/lang/String;)Ljava/util/Collection;"),
            new SelectorResultHook(
                "selector.score-holder-arguments",
                "net/minecraft/commands/arguments/ScoreHolderArgument",
                Set.of("getNames"),
                "(Lcom/mojang/brigadier/context/CommandContext;Ljava/lang/String;Ljava/util/function/Supplier;)Ljava/util/Collection;")
        );
        instrumentation.addTransformer(new ServerPermissionTransformer(hooks), false);
        System.err.println("[VanillaPermissionsPlugin] Native permission agent ready: "
            + hooks.stream().map(NmsFeatureHook::id).toList());
    }

    private static void installBootstrapBridge(Instrumentation instrumentation) {
        try {
            Path jarPath = Files.createTempFile("vanillapermissions-agent-bridge-", ".jar");
            try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
                for (String classPath : BRIDGE_CLASSES) {
                    try (InputStream classBytes = VanillaPermissionsAgent.class.getClassLoader()
                            .getResourceAsStream(classPath)) {
                        if (classBytes == null) throw new IOException("Missing agent bridge class: " + classPath);
                        jar.putNextEntry(new JarEntry(classPath));
                        classBytes.transferTo(jar);
                        jar.closeEntry();
                    }
                }
            }
            jarPath.toFile().deleteOnExit();
            bridgeJar = new JarFile(jarPath.toFile());
            instrumentation.appendToBootstrapClassLoaderSearch(bridgeJar);
            Class.forName("io.github.soul4723.vanillapermissions.agent.PermissionBridge", true, null)
                .getMethod("setAgentActive").invoke(null);
        } catch (IOException | ReflectiveOperationException error) {
            Throwable cause = error instanceof InvocationTargetException invocation
                ? invocation.getCause()
                : error;
            throw new IllegalStateException("Failed to install native agent bridge", cause);
        }
    }
}

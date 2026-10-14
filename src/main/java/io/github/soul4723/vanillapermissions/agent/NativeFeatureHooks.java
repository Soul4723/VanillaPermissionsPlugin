package io.github.soul4723.vanillapermissions.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

final class NativeFeatureHooks {
    private static final String NMS = "net/minecraft/";
    private static final String BRIDGE = "io/github/soul4723/vanillapermissions/agent/PermissionBridge";
    private static final String PLAYER = NMS + "world/entity/player/Player";
    private static final String SERVER_PLAYER = NMS + "server/level/ServerPlayer";
    private static final String CRAFT_HUMAN = "org/bukkit/craftbukkit/entity/CraftHumanEntity";
    private static final String CRAFT_PLAYER = "org/bukkit/craftbukkit/entity/CraftPlayer";
    private static final String CAN_USE_GAME_MASTER_BLOCKS = "()Z";
    private static final String HAS_PERMISSION = "(Ljava/lang/String;)Z";
    private static final String HAS_PERMISSIONS = "(I)Z";

    private NativeFeatureHooks() {}

    static List<NmsFeatureHook> create() {
        return List.of(
            hook("net/minecraft/server/network/ServerGamePacketListenerImpl", List.of(
                calls("handleSetCommandBlock", packet("ServerboundSetCommandBlockPacket"),
                    call(SERVER_PLAYER, "canUseGameMasterBlocks", CAN_USE_GAME_MASTER_BLOCKS,
                        fixed("minecraft.operator_block.command_block.edit")),
                    call(CRAFT_PLAYER, "hasPermission", HAS_PERMISSION,
                        fixed("minecraft.operator_block.command_block.edit"))),
                calls("handleSetCommandMinecart", packet("ServerboundSetCommandMinecartPacket"),
                    call(SERVER_PLAYER, "canUseGameMasterBlocks", CAN_USE_GAME_MASTER_BLOCKS,
                        fixed("minecraft.operator_block.command_block_minecart.edit")),
                    call(CRAFT_PLAYER, "hasPermission", HAS_PERMISSION,
                        fixed("minecraft.operator_block.command_block_minecart.edit"))),
                calls("handleSetStructureBlock", packet("ServerboundSetStructureBlockPacket"),
                    call(SERVER_PLAYER, "canUseGameMasterBlocks", CAN_USE_GAME_MASTER_BLOCKS,
                        fixed("minecraft.operator_block.structure_block.edit"))),
                calls("handleSetJigsawBlock", packet("ServerboundSetJigsawBlockPacket"),
                    call(SERVER_PLAYER, "canUseGameMasterBlocks", CAN_USE_GAME_MASTER_BLOCKS,
                        fixed("minecraft.operator_block.jigsaw.edit"))),
                calls("handleJigsawGenerate", packet("ServerboundJigsawGeneratePacket"),
                    call(SERVER_PLAYER, "canUseGameMasterBlocks", CAN_USE_GAME_MASTER_BLOCKS,
                        fixed("minecraft.operator_block.jigsaw.edit"))),
                calls("handleEntityTagQuery", packet("ServerboundEntityTagQueryPacket"),
                    call(SERVER_PLAYER, "hasPermissions", HAS_PERMISSIONS,
                        fixed("minecraft.nbt.query.entity"))),
                calls("handleBlockEntityTagQuery", packet("ServerboundBlockEntityTagQueryPacket"),
                    call(SERVER_PLAYER, "hasPermissions", HAS_PERMISSIONS,
                        fixed("minecraft.nbt.query.block")))
            )),
            hook("net/minecraft/world/item/BlockItem", List.of(
                calls("updateCustomBlockEntityTag",
                    "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Z",
                    call(PLAYER, "canUseGameMasterBlocks", CAN_USE_GAME_MASTER_BLOCKS,
                        fixed("minecraft.nbt.load.block")),
                    call(CRAFT_HUMAN, "hasPermission", HAS_PERMISSION,
                        fixed("minecraft.nbt.load.block")))
            )),
            hook("net/minecraft/world/entity/EntityType", List.of(
                calls("updateCustomEntityTag",
                    "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/component/CustomData;)V",
                    call("net/minecraft/server/players/PlayerList", "isOp",
                        "(Lcom/mojang/authlib/GameProfile;)Z", argument(1), fixed("minecraft.nbt.load.entity")),
                    call(CRAFT_HUMAN, "hasPermission", HAS_PERMISSION,
                        fixed("minecraft.nbt.load.entity")))
            )),
            hook("net/minecraft/world/item/DebugStickItem", List.of(
                calls("handleInteraction",
                    "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/item/ItemStack;)Z",
                    call(PLAYER, "canUseGameMasterBlocks", CAN_USE_GAME_MASTER_BLOCKS,
                        debugStickBlock(1)),
                    call(CRAFT_HUMAN, "hasPermission", HAS_PERMISSION,
                        debugStickBlock(1), 2))
            )),
            hook("net/minecraft/world/item/GameMasterBlockItem", List.of(
                calls("getPlacementState",
                    "(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;",
                    call(PLAYER, "canUseGameMasterBlocks", CAN_USE_GAME_MASTER_BLOCKS,
                        blockAction(BlockSource.GAME_MASTER_ITEM, ".place")))
            )),
            hook("net/minecraft/server/level/ServerPlayerGameMode", List.of(
                calls("destroyBlock", "(Lnet/minecraft/core/BlockPos;)Z",
                    call(SERVER_PLAYER, "canUseGameMasterBlocks", CAN_USE_GAME_MASTER_BLOCKS,
                        blockAction(BlockSource.BROKEN_BLOCK, ".break")),
                    call(CRAFT_PLAYER, "hasPermission", HAS_PERMISSION,
                        blockAction(BlockSource.BROKEN_BLOCK, ".break")))
            )),
            hook("net/minecraft/world/level/block/CommandBlock", List.of(
                calls("useWithoutItem", blockUseDescriptor(),
                    call(PLAYER, "canUseGameMasterBlocks", CAN_USE_GAME_MASTER_BLOCKS,
                        fixed("minecraft.operator_block.command_block.view")),
                    call(CRAFT_HUMAN, "hasPermission", HAS_PERMISSION,
                        fixed("minecraft.operator_block.command_block.view")))
            )),
            hook("net/minecraft/world/level/block/JigsawBlock", List.of(
                calls("useWithoutItem", blockUseDescriptor(),
                    call(PLAYER, "canUseGameMasterBlocks", CAN_USE_GAME_MASTER_BLOCKS,
                        fixed("minecraft.operator_block.jigsaw.view")))
            )),
            hook("net/minecraft/world/level/block/entity/StructureBlockEntity", List.of(
                calls("usedBy", "(Lnet/minecraft/world/entity/player/Player;)Z",
                    call(PLAYER, "canUseGameMasterBlocks", CAN_USE_GAME_MASTER_BLOCKS,
                        fixed("minecraft.operator_block.structure_block.view")))
            )),
            hook("net/minecraft/world/level/BaseCommandBlock", List.of(
                calls("usedBy", "(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/InteractionResult;",
                    call(PLAYER, "canUseGameMasterBlocks", CAN_USE_GAME_MASTER_BLOCKS,
                        fixed("minecraft.operator_block.command_block_minecart.view")),
                    call(CRAFT_HUMAN, "hasPermission", HAS_PERMISSION,
                        fixed("minecraft.operator_block.command_block_minecart.view")))
            )),
            hook("net/minecraft/util/debugchart/DebugSampleSubscriptionTracker", List.of(
                calls("subscribe",
                    "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/util/debugchart/RemoteDebugSampleType;)V",
                    call("net/minecraft/server/players/PlayerList", "isOp",
                        "(Lcom/mojang/authlib/GameProfile;)Z", argument(0), fixed("minecraft.debug_chart"))),
                calls("lambda$handleUnsubscriptions$0", "(IJLjava/util/Map$Entry;)Z",
                    call("net/minecraft/server/players/PlayerList", "isOp",
                        "(Lcom/mojang/authlib/GameProfile;)Z", mapEntryPlayer(2), fixed("minecraft.debug_chart")))
            ))
        );
    }

    private static NmsFeatureHook hook(String className, List<MethodRule> rules) {
        return new PermissionCallHook(className, rules);
    }

    private static String packet(String packetClass) {
        return "(Lnet/minecraft/network/protocol/game/" + packetClass + ";)V";
    }

    private static String blockUseDescriptor() {
        return "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;";
    }

    private static MethodRule calls(String name, String descriptor, CallRule... calls) {
        return new MethodRule(name, descriptor, List.of(calls));
    }

    private static CallRule call(String owner, String name, String descriptor, PermissionName permission) {
        return new CallRule(owner, name, descriptor, Subject.receiver(), permission, 1);
    }

    private static CallRule call(String owner, String name, String descriptor, Subject subject,
                                 PermissionName permission) {
        return new CallRule(owner, name, descriptor, subject, permission, 1);
    }

    private static CallRule call(String owner, String name, String descriptor, PermissionName permission,
                                 int expectedCount) {
        return new CallRule(owner, name, descriptor, Subject.receiver(), permission, expectedCount);
    }

    private static Subject argument(int local) {
        return (method, receiver) -> method.loadArg(local);
    }

    private static Subject mapEntryPlayer(int argument) {
        return (method, receiver) -> {
            method.loadArg(argument);
            method.invokeInterface(Type.getType(Map.Entry.class), new Method("getKey", "()Ljava/lang/Object;"));
            method.checkCast(Type.getObjectType(SERVER_PLAYER));
        };
    }

    private static PermissionName fixed(String permission) {
        return method -> method.push(permission);
    }

    private static PermissionName debugStickBlock(int blockStateLocal) {
        return method -> {
            int block = blockLocal(method, BlockSource.methodArg(blockStateLocal));
            int key = blockKeyLocal(method, block);
            method.newInstance(Type.getType(StringBuilder.class));
            method.dup();
            method.push("minecraft.debug_stick.use.");
            method.invokeConstructor(Type.getType(StringBuilder.class),
                new Method("<init>", "(Ljava/lang/String;)V"));
            method.loadLocal(key);
            method.invokeVirtual(Type.getObjectType("net/minecraft/resources/ResourceLocation"),
                new Method("getNamespace", "()Ljava/lang/String;"));
            append(method);
            method.push(".");
            append(method);
            method.loadLocal(key);
            method.invokeVirtual(Type.getObjectType("net/minecraft/resources/ResourceLocation"),
                new Method("getPath", "()Ljava/lang/String;"));
            append(method);
            method.invokeVirtual(Type.getType(StringBuilder.class), new Method("toString", "()Ljava/lang/String;"));
        };
    }

    private static PermissionName blockAction(BlockSource source, String action) {
        return method -> {
            int block = blockLocal(method, source);
            Type registryType = Type.getObjectType("net/minecraft/core/registries/BuiltInRegistries");
            Type blockType = Type.getObjectType(NMS + "world/level/block/Block");
            Type registry = Type.getObjectType("net/minecraft/core/Registry");
            method.newInstance(Type.getType(StringBuilder.class));
            method.dup();
            method.push("minecraft.operator_block.");
            method.invokeConstructor(Type.getType(StringBuilder.class),
                new Method("<init>", "(Ljava/lang/String;)V"));
            resourceKey(method, registryType, blockType, registry, block);
            method.invokeVirtual(Type.getObjectType("net/minecraft/resources/ResourceLocation"),
                new Method("getPath", "()Ljava/lang/String;"));
            append(method);
            method.push(action);
            append(method);
            method.invokeVirtual(Type.getType(StringBuilder.class), new Method("toString", "()Ljava/lang/String;"));
        };
    }

    private static int blockLocal(AdviceAdapter method, BlockSource source) {
        Type block = Type.getObjectType(NMS + "world/level/block/Block");
        switch (source.kind()) {
            case GAME_MASTER_ITEM -> {
                method.loadThis();
                method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, NMS + "world/item/BlockItem", "getBlock",
                    "()L" + NMS + "world/level/block/Block;", false);
            }
            case METHOD_ARG -> {
                method.loadArg(source.local());
                method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, NMS + "world/level/block/state/BlockState", "getBlock",
                    "()L" + NMS + "world/level/block/Block;", false);
            }
            case BROKEN_BLOCK -> {
                method.loadThis();
                method.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/server/level/ServerPlayerGameMode", "level",
                    "Lnet/minecraft/server/level/ServerLevel;");
                method.loadArg(0);
                method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/server/level/ServerLevel", "getBlockState",
                    "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", false);
                method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, NMS + "world/level/block/state/BlockState", "getBlock",
                    "()L" + NMS + "world/level/block/Block;", false);
            }
            default -> throw new IllegalStateException("Unsupported block source: " + source.kind());
        }
        int local = method.newLocal(block);
        method.storeLocal(local);
        return local;
    }

    private static int blockKeyLocal(AdviceAdapter method, int blockLocal) {
        Type key = Type.getObjectType("net/minecraft/resources/ResourceLocation");
        resourceKey(method, Type.getObjectType("net/minecraft/core/registries/BuiltInRegistries"),
            Type.getObjectType(NMS + "world/level/block/Block"), Type.getObjectType("net/minecraft/core/Registry"),
            blockLocal);
        int local = method.newLocal(key);
        method.storeLocal(local);
        return local;
    }

    private static void resourceKey(AdviceAdapter method, Type registryOwner, Type valueType, Type registryType,
                                    int valueLocal) {
        method.getStatic(registryOwner, "BLOCK", Type.getObjectType("net/minecraft/core/DefaultedRegistry"));
        method.loadLocal(valueLocal, valueType);
        method.invokeInterface(registryType,
            new Method("getKey", "(Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;"));
    }

    private static void append(AdviceAdapter method) {
        method.invokeVirtual(Type.getType(StringBuilder.class),
            new Method("append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
    }

    private record BlockSource(Kind kind, int local) {
        private enum Kind { GAME_MASTER_ITEM, METHOD_ARG, BROKEN_BLOCK }
        static final BlockSource GAME_MASTER_ITEM = new BlockSource(Kind.GAME_MASTER_ITEM, -1);
        static final BlockSource BROKEN_BLOCK = new BlockSource(Kind.BROKEN_BLOCK, -1);
        static BlockSource methodArg(int local) { return new BlockSource(Kind.METHOD_ARG, local); }
    }

    @FunctionalInterface
    private interface PermissionName {
        void emit(AdviceAdapter method);
    }

    @FunctionalInterface
    private interface Subject {
        void emit(AdviceAdapter method, int receiverLocal);
        static Subject receiver() { return (method, receiver) -> method.loadLocal(receiver); }
    }

    private record MethodRule(String name, String descriptor, List<CallRule> calls) {}
    private record CallRule(String owner, String name, String descriptor, Subject subject,
                            PermissionName permission, int expectedCount) {}

    private static final class PermissionCallHook implements NmsFeatureHook {
        private final String targetClass;
        private final List<MethodRule> rules;

        private PermissionCallHook(String targetClass, List<MethodRule> rules) {
            this.targetClass = targetClass;
            this.rules = rules;
        }

        @Override public String id() { return "native-features." + targetClass.replace('/', '.'); }
        @Override public String targetClass() { return targetClass; }

        @Override
        public byte[] transform(byte[] classBytes) {
            Map<CallRule, Integer> found = new HashMap<>();
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES) {
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    return "java/lang/Object";
                }
            };
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                 String[] exceptions) {
                    MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                    MethodRule rule = rules.stream().filter(candidate -> candidate.name().equals(name)
                        && candidate.descriptor().equals(descriptor)).findFirst().orElse(null);
                    if (rule == null) return delegate;
                    return new AdviceAdapter(Opcodes.ASM9, delegate, access, name, descriptor) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            CallRule call = rule.calls().stream().filter(candidate -> candidate.owner().equals(owner)
                                && candidate.name().equals(methodName)
                                && candidate.descriptor().equals(methodDescriptor)).findFirst().orElse(null);
                            if (call == null) {
                                super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                                return;
                            }

                            if (Type.getReturnType(methodDescriptor).getSort() != Type.BOOLEAN) {
                                throw new IllegalStateException("Permission hook target must return boolean: " + owner
                                    + "." + methodName + methodDescriptor);
                            }
                            Type[] arguments = Type.getArgumentTypes(methodDescriptor);
                            int[] argumentLocals = new int[arguments.length];
                            for (int index = arguments.length - 1; index >= 0; index--) {
                                argumentLocals[index] = newLocal(arguments[index]);
                                storeLocal(argumentLocals[index], arguments[index]);
                            }
                            int receiver = newLocal(Type.getObjectType(owner));
                            storeLocal(receiver);
                            loadLocal(receiver);
                            for (int index = 0; index < arguments.length; index++) {
                                loadLocal(argumentLocals[index], arguments[index]);
                            }
                            super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                            int original = newLocal(Type.BOOLEAN_TYPE);
                            storeLocal(original, Type.BOOLEAN_TYPE);
                            call.subject().emit(this, receiver);
                            call.permission().emit(this);
                            loadLocal(original, Type.BOOLEAN_TYPE);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "check",
                                "(Ljava/lang/Object;Ljava/lang/String;Z)Z", false);
                            found.merge(call, 1, Integer::sum);
                        }
                    };
                }
            };
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            for (MethodRule rule : rules) {
                for (CallRule call : rule.calls()) {
                    int matches = found.getOrDefault(call, 0);
                    if (matches != call.expectedCount()) {
                        throw new IllegalStateException("Expected " + call.expectedCount() + " calls in "
                            + targetClass + "." + rule.name() + rule.descriptor() + " but found " + matches
                            + " for " + call.owner() + "." + call.name() + call.descriptor());
                    }
                }
            }
            return writer.toByteArray();
        }
    }
}

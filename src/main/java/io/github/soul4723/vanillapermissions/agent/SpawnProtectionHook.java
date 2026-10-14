package io.github.soul4723.vanillapermissions.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

final class SpawnProtectionHook implements NmsFeatureHook {
    private static final String METHOD = "isUnderSpawnProtection";
    private static final String METHOD_DESCRIPTOR = "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z";
    private static final String OP_CHECK_OWNER = "net/minecraft/server/dedicated/DedicatedPlayerList";
    private static final String OP_CHECK_NAME = "isOp";
    private static final String OP_CHECK_DESCRIPTOR = "(Lcom/mojang/authlib/GameProfile;)Z";
    private static final String BRIDGE = "io/github/soul4723/vanillapermissions/agent/PermissionBridge";
    private boolean changed;

    @Override
    public String id() {
        return "bypass.spawn-protection";
    }

    @Override
    public String targetClass() {
        return "net/minecraft/server/dedicated/DedicatedServer";
    }

    @Override
    public byte[] transform(byte[] classBytes) {
        changed = false;
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!METHOD.equals(name) || !METHOD_DESCRIPTOR.equals(descriptor)) return method;
                return new AdviceAdapter(Opcodes.ASM9, method, access, name, descriptor) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if (opcode != INVOKEVIRTUAL || !OP_CHECK_OWNER.equals(owner)
                            || !OP_CHECK_NAME.equals(methodName) || !OP_CHECK_DESCRIPTOR.equals(methodDescriptor)) {
                            super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                            return;
                        }

                        Type[] argumentTypes = Type.getArgumentTypes(methodDescriptor);
                        int[] arguments = new int[argumentTypes.length];
                        for (int index = argumentTypes.length - 1; index >= 0; index--) {
                            arguments[index] = newLocal(argumentTypes[index]);
                            storeLocal(arguments[index], argumentTypes[index]);
                        }
                        int receiver = newLocal(Type.getObjectType(owner));
                        storeLocal(receiver);
                        loadLocal(receiver);
                        for (int index = 0; index < argumentTypes.length; index++) {
                            loadLocal(arguments[index], argumentTypes[index]);
                        }
                        super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);

                        int original = newLocal(Type.BOOLEAN_TYPE);
                        storeLocal(original, Type.BOOLEAN_TYPE);
                        loadLocal(3);
                        push("minecraft.bypass.spawn-protection");
                        loadLocal(original, Type.BOOLEAN_TYPE);
                        super.visitMethodInsn(INVOKESTATIC, BRIDGE, "check",
                            "(Ljava/lang/Object;Ljava/lang/String;Z)Z", false);
                        changed = true;
                    }
                };
            }
        };
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        if (!changed) throw new IllegalStateException("Expected op check not found in isUnderSpawnProtection");
        return writer.toByteArray();
    }
}

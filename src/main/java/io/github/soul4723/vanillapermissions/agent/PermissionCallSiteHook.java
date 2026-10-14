package io.github.soul4723.vanillapermissions.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

final class PermissionCallSiteHook implements NmsFeatureHook {
    private static final String BRIDGE = "io/github/soul4723/vanillapermissions/agent/PermissionBridge";

    private final String id;
    private final String targetClass;
    private final String targetMethod;
    private final String targetMethodDescriptor;
    private final String checkedOwner;
    private final String checkedMethod;
    private final String checkedMethodDescriptor;
    private final String permission;
    private final int subjectLocal;
    private boolean changed;

    PermissionCallSiteHook(String id, String targetClass, String targetMethod, String targetMethodDescriptor,
                           String checkedOwner, String checkedMethod, String checkedMethodDescriptor,
                           String permission, int subjectLocal) {
        this.id = id;
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.targetMethodDescriptor = targetMethodDescriptor;
        this.checkedOwner = checkedOwner;
        this.checkedMethod = checkedMethod;
        this.checkedMethodDescriptor = checkedMethodDescriptor;
        this.permission = permission;
        this.subjectLocal = subjectLocal;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String targetClass() {
        return targetClass;
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
                if (!targetMethod.equals(name) || !targetMethodDescriptor.equals(descriptor)) return method;
                return new AdviceAdapter(Opcodes.ASM9, method, access, name, descriptor) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                        if (opcode != INVOKEVIRTUAL || !checkedOwner.equals(owner)
                            || !checkedMethod.equals(methodName)
                            || !checkedMethodDescriptor.equals(methodDescriptor)) return;

                        int original = newLocal(Type.BOOLEAN_TYPE);
                        storeLocal(original, Type.BOOLEAN_TYPE);
                        visitVarInsn(ALOAD, subjectLocal);
                        push(permission);
                        loadLocal(original, Type.BOOLEAN_TYPE);
                        super.visitMethodInsn(INVOKESTATIC, BRIDGE, "check",
                            "(Ljava/lang/Object;Ljava/lang/String;Z)Z", false);
                        changed = true;
                    }
                };
            }
        };
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        if (!changed) throw new IllegalStateException("Expected permission call not found in " + targetMethod);
        return writer.toByteArray();
    }
}

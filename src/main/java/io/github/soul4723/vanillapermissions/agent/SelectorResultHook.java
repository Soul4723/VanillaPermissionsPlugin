package io.github.soul4723.vanillapermissions.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import java.util.Set;

final class SelectorResultHook implements NmsFeatureHook {
    private static final String CONTEXT_ARGUMENTS =
        "(Lcom/mojang/brigadier/context/CommandContext;Ljava/lang/String;";
    private static final String BRIDGE = "io/github/soul4723/vanillapermissions/agent/SelectorBridge";

    private final String id;
    private final String targetClass;
    private final Set<String> methodNames;
    private final String descriptor;
    private boolean changed;

    SelectorResultHook(String id, String targetClass, Set<String> methodNames, String descriptor) {
        this.id = id;
        this.targetClass = targetClass;
        this.methodNames = Set.copyOf(methodNames);
        this.descriptor = descriptor;
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
            public MethodVisitor visitMethod(int access, String name, String methodDescriptor, String signature,
                                             String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, methodDescriptor, signature, exceptions);
                if ((access & Opcodes.ACC_STATIC) == 0 || !methodNames.contains(name)
                    || !methodDescriptor.startsWith(CONTEXT_ARGUMENTS)
                    || (descriptor != null && !descriptor.equals(methodDescriptor))) return method;

                Type resultType = Type.getReturnType(methodDescriptor);
                if (resultType.getSort() != Type.OBJECT && resultType.getSort() != Type.ARRAY) return method;
                return new AdviceAdapter(Opcodes.ASM9, method, access, name, methodDescriptor) {
                    @Override
                    protected void onMethodExit(int opcode) {
                        if (opcode != ARETURN) return;
                        int result = newLocal(resultType);
                        storeLocal(result, resultType);
                        visitVarInsn(ALOAD, 0);
                        visitVarInsn(ALOAD, 1);
                        loadLocal(result, resultType);
                        super.visitMethodInsn(INVOKESTATIC, BRIDGE, "validate",
                            "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V", false);
                        loadLocal(result, resultType);
                        changed = true;
                    }
                };
            }
        };
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        if (!changed) throw new IllegalStateException("Expected selector result getter not found in " + targetClass);
        return writer.toByteArray();
    }
}

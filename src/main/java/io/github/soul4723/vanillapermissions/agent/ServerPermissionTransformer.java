package io.github.soul4723.vanillapermissions.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.List;

final class ServerPermissionTransformer implements ClassFileTransformer {
    private final List<NmsFeatureHook> hooks;

    ServerPermissionTransformer(List<NmsFeatureHook> hooks) {
        this.hooks = List.copyOf(hooks);
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classBytes)
        throws IllegalClassFormatException {
        byte[] transformed = classBytes;
        for (NmsFeatureHook hook : hooks) {
            if (!hook.targetClass().equals(className)) continue;
            try {
                transformed = hook.transform(transformed);
                System.err.println("[VanillaPermissionsPlugin] Applied native hook: " + hook.id());
            } catch (Exception error) {
                IllegalClassFormatException failure = new IllegalClassFormatException(
                    "VanillaPermissionsPlugin failed to apply " + hook.id() + " to " + className);
                failure.initCause(error);
                throw failure;
            }
        }
        return transformed == classBytes ? null : transformed;
    }
}

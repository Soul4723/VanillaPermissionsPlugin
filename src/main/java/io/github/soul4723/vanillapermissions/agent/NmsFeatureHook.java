package io.github.soul4723.vanillapermissions.agent;

public interface NmsFeatureHook {
    String id();
    String targetClass();
    byte[] transform(byte[] classBytes) throws Exception;
}

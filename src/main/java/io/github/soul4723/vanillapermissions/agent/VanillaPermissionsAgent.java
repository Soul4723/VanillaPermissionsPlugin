package io.github.soul4723.vanillapermissions.agent;

import java.lang.instrument.Instrumentation;
import java.util.List;

public final class VanillaPermissionsAgent {
    private VanillaPermissionsAgent() {}

    public static void premain(String arguments, Instrumentation instrumentation) {
        List<NmsFeatureHook> hooks = List.of(new SpawnProtectionHook());
        instrumentation.addTransformer(new ServerPermissionTransformer(hooks), false);
        PermissionBridge.setAgentActive();
        System.err.println("[VanillaPermissionsPlugin] Native permission agent ready: "
            + hooks.stream().map(NmsFeatureHook::id).toList());
    }
}

import io.github.soul4723.vanillapermissions.agent.NativeAgentBridge;

public final class NativeAgentMissingCheck {
    private NativeAgentMissingCheck() {}

    public static void main(String[] args) {
        if (NativeAgentBridge.isActive()) throw new AssertionError("agent unexpectedly active");
        NativeAgentBridge.clearPermissionCheck();
        NativeAgentBridge.clearSelectorCheck();
    }
}

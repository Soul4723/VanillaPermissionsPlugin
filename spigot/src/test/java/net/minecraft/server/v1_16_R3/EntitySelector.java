package net.minecraft.server.v1_16_R3;

/** Four non-static booleans; only checkPermissions is true exactly for selectors. */
public class EntitySelector {

    private boolean always = true;
    private boolean never = false;
    private boolean checkPermissions;
    private boolean onlyEverything;

    public EntitySelector(String input) {
        this.checkPermissions = input.startsWith("@");
        this.onlyEverything = input.startsWith("@e");
    }

    public boolean usesSelector() {
        return checkPermissions;
    }
}

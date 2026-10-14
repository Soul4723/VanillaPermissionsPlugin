package io.github.soul4723.vanillapermissions.util;

public class SelectorPermissionMappingCheck {
    public static void main(String[] args) {
        assert SelectorPermissionCheck.permissionForParsedSelector("@s")
                .equals("minecraft.selector.self.s");
        assert SelectorPermissionCheck.permissionForParsedSelector("@a[limit=2]")
                .equals("minecraft.selector.player.a");
        assert SelectorPermissionCheck.permissionForParsedSelector("@p")
                .equals("minecraft.selector.player.p");
        assert SelectorPermissionCheck.permissionForParsedSelector("@r")
                .equals("minecraft.selector.player.r");
        assert SelectorPermissionCheck.permissionForParsedSelector("@e[type=zombie]")
                .equals("minecraft.selector.entity.e");
        assert SelectorPermissionCheck.permissionForParsedSelector("Alex") == null;
        assert SelectorPermissionCheck.permissionForParsedSelector("{\"text\":\"@e\"}") == null;
        assert SelectorPermissionCheck.permissionForParsedSelector("@x") == null;
        System.out.println("Parsed selector permission mapping checks passed");
    }
}

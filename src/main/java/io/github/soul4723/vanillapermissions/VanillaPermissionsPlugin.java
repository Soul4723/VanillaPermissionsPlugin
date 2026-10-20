package io.github.soul4723.vanillapermissions;

import io.github.soul4723.vanillapermissions.command.VanillaPermissionsPluginCommand;
import io.github.soul4723.vanillapermissions.listener.DebugStickListener;
import io.github.soul4723.vanillapermissions.listener.ForceGamemodeBypassListener;
import io.github.soul4723.vanillapermissions.listener.LuckPermsPreLoginListener;
import io.github.soul4723.vanillapermissions.listener.MoveSpeedBypassListener;
import io.github.soul4723.vanillapermissions.listener.OperatorBlockListener;
import io.github.soul4723.vanillapermissions.listener.WhitelistBypassListener;
import io.github.soul4723.vanillapermissions.util.LuckPermsPermissionProvider;
import io.github.soul4723.vanillapermissions.util.NativeCommandPermissions;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import io.github.soul4723.vanillapermissions.util.PermissionProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class VanillaPermissionsPlugin extends JavaPlugin {

    /** The only Paper version whose native command tree this plugin has been verified against. */
    public static final String TESTED_MINECRAFT_VERSION = "1.20.6";

    private PermissionProvider permissionProvider;

    @Override
    public void onEnable() {
        String minecraftVersion = getServer().getMinecraftVersion();
        if (!TESTED_MINECRAFT_VERSION.equals(minecraftVersion)) {
            getLogger().warning("This server runs Minecraft " + minecraftVersion + ", but VanillaPermissionsPlugin is only tested on Paper "
                + TESTED_MINECRAFT_VERSION + ", so adding the vanilla command checks may fail");
        }

        permissionProvider = PermissionManager.selectProvider(this);
        PermissionManager.initialize(permissionProvider, this);

        VanillaPermissionsPluginCommand.register(this);
        registerListeners();
        NativeCommandPermissions.register(this);
    }

    private void registerListeners() {
        if (permissionProvider instanceof LuckPermsPermissionProvider luckPerms) {
            getServer().getPluginManager().registerEvents(
                new LuckPermsPreLoginListener(luckPerms.getHook().getLuckPerms(), getLogger()), this);
        }
        getServer().getPluginManager().registerEvents(new DebugStickListener(), this);
        getServer().getPluginManager().registerEvents(new OperatorBlockListener(), this);
        getServer().getPluginManager().registerEvents(new WhitelistBypassListener(), this);
        getServer().getPluginManager().registerEvents(new ForceGamemodeBypassListener(), this);
        getServer().getPluginManager().registerEvents(new MoveSpeedBypassListener(), this);
    }

    @Override
    public void onDisable() {
        if (permissionProvider != null) permissionProvider.close();
        NativeCommandPermissions.uninstall();
    }
}

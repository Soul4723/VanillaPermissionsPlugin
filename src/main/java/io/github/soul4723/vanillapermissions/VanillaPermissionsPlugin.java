package io.github.soul4723.vanillapermissions;

import io.github.soul4723.vanillapermissions.command.VanillaPermissionsPluginCommand;
import io.github.soul4723.vanillapermissions.listener.DebugStickListener;
import io.github.soul4723.vanillapermissions.listener.ForceGamemodeBypassListener;
import io.github.soul4723.vanillapermissions.listener.LuckPermsPreLoginListener;
import io.github.soul4723.vanillapermissions.listener.MoveSpeedBypassListener;
import io.github.soul4723.vanillapermissions.listener.OperatorBlockListener;
import io.github.soul4723.vanillapermissions.listener.WhitelistBypassListener;
import io.github.soul4723.vanillapermissions.util.LuckPermsHook;
import io.github.soul4723.vanillapermissions.util.NativeCommandPermissions;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.plugin.java.JavaPlugin;

public class VanillaPermissionsPlugin extends JavaPlugin {

    private LuckPermsHook luckPermsHook;

    @Override
    public void onEnable() {
        luckPermsHook = new LuckPermsHook();
        luckPermsHook.initialize(this);

        if (!luckPermsHook.isEnabled()) {
            getLogger().severe("Failed to initialize LuckPerms integration!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PermissionManager.initialize(luckPermsHook, this);

        VanillaPermissionsPluginCommand.registerCommands(this);
        registerListeners();
        NativeCommandPermissions.register(this);

        getLogger().info("VanillaPermissionsPlugin v" + getDescription().getVersion()
            + " enabled (Paper port inspired by DrexHD's VanillaPermissions)");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
            new LuckPermsPreLoginListener(luckPermsHook.getLuckPerms(), getLogger()), this);
        getServer().getPluginManager().registerEvents(new DebugStickListener(), this);
        getServer().getPluginManager().registerEvents(new OperatorBlockListener(), this);
        getServer().getPluginManager().registerEvents(new WhitelistBypassListener(), this);
        getServer().getPluginManager().registerEvents(new ForceGamemodeBypassListener(), this);
        getServer().getPluginManager().registerEvents(new MoveSpeedBypassListener(), this);
    }

    @Override
    public void onDisable() {
        NativeCommandPermissions.uninstall();
        getLogger().info("VanillaPermissionsPlugin disabled");
    }
}

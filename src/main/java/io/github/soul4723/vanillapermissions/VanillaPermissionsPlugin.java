package io.github.soul4723.vanillapermissions;

import io.github.soul4723.vanillapermissions.command.VanillaPermissionsPluginCommand;
import io.github.soul4723.vanillapermissions.listener.DebugStickListener;
import io.github.soul4723.vanillapermissions.listener.ForceGamemodeBypassListener;
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
        saveDefaultConfig();

        if (!validateConfiguration()) {
            getLogger().severe("Configuration validation failed! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

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
        if (isFeatureEnabled("command_permissions")) {
            NativeCommandPermissions.register(this);
        }

        getLogger().info("VanillaPermissionsPlugin v" + getDescription().getVersion()
            + " enabled (Paper port inspired by DrexHD's VanillaPermissions)");
    }

    private void registerListeners() {
        if (isFeatureEnabled("admin_permissions")) {
            getServer().getPluginManager().registerEvents(new DebugStickListener(), this);
            getServer().getPluginManager().registerEvents(new OperatorBlockListener(), this);
        }

        if (isFeatureEnabled("bypass_permissions")) {
            getServer().getPluginManager().registerEvents(new WhitelistBypassListener(), this);
            getServer().getPluginManager().registerEvents(new ForceGamemodeBypassListener(), this);
            getServer().getPluginManager().registerEvents(new MoveSpeedBypassListener(), this);
        }
    }

    public boolean isFeatureEnabled(String feature) {
        return getConfig().getBoolean("features." + feature, true);
    }

    private boolean validateConfiguration() {
        if (!getConfig().isConfigurationSection("features")) {
            getLogger().warning("Missing or invalid 'features' section in config.yml");
            return false;
        }

        String[] features = {"command_permissions", "bypass_permissions", "admin_permissions"};
        for (String feature : features) {
            String path = "features." + feature;
            if (getConfig().contains(path) && !(getConfig().get(path) instanceof Boolean)) {
                getLogger().warning("Feature '" + feature + "' must be true or false");
                return false;
            }
        }

        return true;
    }

    public void reloadPluginConfig() {
        reloadConfig();
        getLogger().info("Configuration reloaded - restart plugin for full effect");
    }

    @Override
    public void onDisable() {
        NativeCommandPermissions.uninstall();
        getLogger().info("VanillaPermissionsPlugin disabled");
    }
}

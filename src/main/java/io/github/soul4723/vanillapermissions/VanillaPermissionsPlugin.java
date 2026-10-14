package io.github.soul4723.vanillapermissions;

import io.github.soul4723.vanillapermissions.command.DifficultyCommandHandler;
import io.github.soul4723.vanillapermissions.command.EffectCommandHandler;
import io.github.soul4723.vanillapermissions.command.VanillaPermissionsPluginCommand;
import io.github.soul4723.vanillapermissions.command.GameruleCommandHandler;
import io.github.soul4723.vanillapermissions.command.TimeCommandHandler;
import io.github.soul4723.vanillapermissions.command.WeatherCommandHandler;
import io.github.soul4723.vanillapermissions.command.WhitelistCommandHandler;
import io.github.soul4723.vanillapermissions.listener.DebugStickListener;
import io.github.soul4723.vanillapermissions.listener.ForceGamemodeBypassListener;
import io.github.soul4723.vanillapermissions.listener.MoveSpeedBypassListener;
import io.github.soul4723.vanillapermissions.listener.OperatorBlockListener;
import io.github.soul4723.vanillapermissions.listener.SelectorPermissionListener;
import io.github.soul4723.vanillapermissions.listener.WhitelistBypassListener;
import io.github.soul4723.vanillapermissions.util.LuckPermsHook;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import org.bukkit.plugin.java.JavaPlugin;

public class VanillaPermissionsPlugin extends JavaPlugin {

    private LuckPermsHook luckPermsHook;

    private boolean checkDependencies() {
        boolean hasAll = true;

        if (getServer().getPluginManager().getPlugin("CommandAPI") == null) {
            getLogger().severe("VanillaPermissionsPlugin requires CommandAPI to function!");
            getLogger().severe("Download: https://github.com/CommandAPI/CommandAPI/releases");
            hasAll = false;
        }

        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().severe("VanillaPermissionsPlugin requires LuckPerms to function!");
            getLogger().severe("Download: https://luckperms.net/download");
            hasAll = false;
        }

        return hasAll;
    }


    @Override
    public void onEnable() {
        if (!checkDependencies()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

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
        registerGranularCommands();
        registerListeners();

        getLogger().info("VanillaPermissionsPlugin v" + getDescription().getVersion() + " enabled (inspired by VanillaPermissions)");
    }

    private void registerGranularCommands() {
        if (isFeatureEnabled("command_permissions")) {
            try {
                WhitelistCommandHandler.registerCommands(this);
                TimeCommandHandler.registerCommands(this);
                WeatherCommandHandler.registerCommands(this);
                GameruleCommandHandler.registerCommands(this);
                DifficultyCommandHandler.registerCommands(this);
                EffectCommandHandler.registerCommands(this);
                getLogger().info("Granular commands registered successfully");
            } catch (Exception e) {
                getLogger().warning("Failed to register granular commands: " + e.getMessage());
            }
        }
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

        if (isFeatureEnabled("selector_permissions")) {
            getServer().getPluginManager().registerEvents(new SelectorPermissionListener(), this);
        }
    }

    public boolean isFeatureEnabled(String feature) {
        return getConfig().getBoolean("features." + feature, true);
    }

    public boolean isOpsBypassAll() {
        return getConfig().getBoolean("default_permissions.ops_bypass_all", true);
    }

    private boolean validateConfiguration() {
        if (!getConfig().contains("features")) {
            getLogger().warning("Missing 'features' section in config.yml");
            return false;
        }

        String[] features = {"command_permissions", "selector_permissions", "bypass_permissions", "admin_permissions"};
        for (String feature : features) {
            String path = "features." + feature;
            if (getConfig().contains(path) && !(getConfig().get(path) instanceof Boolean)) {
                getLogger().warning("Feature '" + feature + "' must be true or false");
                return false;
            }
        }

        if (getConfig().contains("default_permissions.ops_bypass_all")) {
            if (!(getConfig().get("default_permissions.ops_bypass_all") instanceof Boolean)) {
                getLogger().warning("default_permissions.ops_bypass_all must be true or false");
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
        getLogger().info("VanillaPermissionsPlugin disabled");
    }
}

package io.github.soul4723.extrapermissions;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;
import io.github.soul4723.extrapermissions.command.ExtraPermissionsCommand;
import io.github.soul4723.extrapermissions.listener.AdminBroadcastListener;
import io.github.soul4723.extrapermissions.listener.ChatSpeedBypassListener;

import io.github.soul4723.extrapermissions.listener.DebugStickListener;
import io.github.soul4723.extrapermissions.listener.MovementSpeedBypassListener;
import io.github.soul4723.extrapermissions.listener.NBTPermissionListener;
import io.github.soul4723.extrapermissions.listener.OperatorBlockListener;
import io.github.soul4723.extrapermissions.listener.SelectorPermissionListener;
import io.github.soul4723.extrapermissions.listener.WhitelistBypassListener;
import io.github.soul4723.extrapermissions.util.LuckPermsHook;
import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ExtraPermissions extends JavaPlugin {
    
    private LuckPermsHook luckPermsHook;
    
    @Override
    public void onLoad() {
        CommandAPI.onLoad(new CommandAPIBukkitConfig(this));
    }
    
    @Override
    public void onEnable() {
        // Load configuration
        saveDefaultConfig();
        
        // Validate configuration
        if (!validateConfiguration()) {
            getLogger().severe("Configuration validation failed! Using default values.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        luckPermsHook = new LuckPermsHook();
        luckPermsHook.initialize(this);

        if (!luckPermsHook.isEnabled()) {
            getLogger().severe("ExtraPermissions requires LuckPerms to function!");
            getLogger().severe("Please install LuckPerms: https://luckperms.net/");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PermissionManager.initialize(luckPermsHook);

        CommandAPI.onEnable();

        ExtraPermissionsCommand.registerCommands(this);

        // Register listeners based on feature toggles
        registerListeners();
        
        getLogger().info("ExtraPermissions v2.0.0 enabled with LuckPerms integration");
    }
    
    private void registerListeners() {
        // Admin permissions feature
        if (isFeatureEnabled("admin_permissions")) {
            getServer().getPluginManager().registerEvents(new AdminBroadcastListener(), this);
            getServer().getPluginManager().registerEvents(new DebugStickListener(), this);
            getServer().getPluginManager().registerEvents(new OperatorBlockListener(), this);
        }
        
        // Bypass permissions feature
        if (isFeatureEnabled("bypass_permissions")) {
            getServer().getPluginManager().registerEvents(new ChatSpeedBypassListener(), this);
            getServer().getPluginManager().registerEvents(new MovementSpeedBypassListener(), this);
            getServer().getPluginManager().registerEvents(new WhitelistBypassListener(), this);
        }
        
        // Selector permissions feature
        if (isFeatureEnabled("selector_permissions")) {
            getServer().getPluginManager().registerEvents(new SelectorPermissionListener(), this);
        }
        
        // Debug features
        if (isFeatureEnabled("debug_features")) {
            getServer().getPluginManager().registerEvents(new NBTPermissionListener(), this);
        }
        
        // Note: command_permissions feature is handled in command registration
    }
    
    /**
     * Check if a feature is enabled in configuration
     * @param feature The feature name to check
     * @return true if feature is enabled, false otherwise
     */
    public boolean isFeatureEnabled(String feature) {
        return getConfig().getBoolean("features." + feature, true);
    }
    
    /**
     * Validate the plugin configuration
     * @return true if configuration is valid, false otherwise
     */
    private boolean validateConfiguration() {
        boolean isValid = true;
        
        // Check if features section exists
        if (!getConfig().contains("features")) {
            getLogger().warning("Missing 'features' section in config.yml");
            isValid = false;
        }
        
        // Validate each feature is a boolean
        String[] features = {"command_permissions", "selector_permissions", "bypass_permissions", "admin_permissions", "debug_features"};
        for (String feature : features) {
            String path = "features." + feature;
            if (getConfig().contains(path) && !(getConfig().get(path) instanceof Boolean)) {
                getLogger().warning("Feature '" + feature + "' must be true or false");
                isValid = false;
            }
        }
        
        return isValid;
    }
    
    public void reloadPluginConfig() {
        reloadConfig();
        // Clear permission cache to ensure new permissions are checked
        PermissionManager.clearCache();
        
        // Re-register listeners with new configuration
        // Note: In a production environment, you'd want to properly unregister existing listeners
        getLogger().info("Configuration reloaded - restart plugin for full effect");
    }
    
    @Override
    public void onDisable() {
        CommandAPI.onDisable();
        getLogger().info("ExtraPermissions disabled");
    }
}
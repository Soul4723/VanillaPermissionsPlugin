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
        saveDefaultConfig();
        
        if (!validateConfiguration()) {
            getLogger().severe("Configuration validation failed! Disabling plugin.");
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
        registerListeners();
        
        getLogger().info("ExtraPermissions v2.0.0 enabled (inspired by VanillaPermissions)");
    }
    
    private void registerListeners() {
        if (isFeatureEnabled("admin_permissions")) {
            getServer().getPluginManager().registerEvents(new AdminBroadcastListener(), this);
            getServer().getPluginManager().registerEvents(new DebugStickListener(), this);
            getServer().getPluginManager().registerEvents(new OperatorBlockListener(), this);
        }
        
        if (isFeatureEnabled("bypass_permissions")) {
            getServer().getPluginManager().registerEvents(new ChatSpeedBypassListener(), this);
            getServer().getPluginManager().registerEvents(new MovementSpeedBypassListener(), this);
            getServer().getPluginManager().registerEvents(new WhitelistBypassListener(), this);
        }
        
        if (isFeatureEnabled("selector_permissions")) {
            getServer().getPluginManager().registerEvents(new SelectorPermissionListener(), this);
        }
        
        if (isFeatureEnabled("nbt_permissions")) {
            getServer().getPluginManager().registerEvents(new NBTPermissionListener(), this);
        }
    }
    
    public boolean isFeatureEnabled(String feature) {
        return getConfig().getBoolean("features." + feature, true);
    }
    
    private boolean validateConfiguration() {
        if (!getConfig().contains("features")) {
            getLogger().warning("Missing 'features' section in config.yml");
            return false;
        }
        
        String[] features = {"command_permissions", "selector_permissions", "bypass_permissions", "admin_permissions", "debug_features", "nbt_permissions"};
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
        PermissionManager.clearCache();
        getLogger().info("Configuration reloaded - restart plugin for full effect");
    }
    
    @Override
    public void onDisable() {
        CommandAPI.onDisable();
        getLogger().info("ExtraPermissions disabled");
    }
}
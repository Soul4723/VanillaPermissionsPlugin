package io.github.soul4723.extrapermissions;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;
import io.github.soul4723.extrapermissions.command.DifficultyCommandHandler;
import io.github.soul4723.extrapermissions.command.EffectCommandHandler;
import io.github.soul4723.extrapermissions.command.ExtraPermissionsCommand;
import io.github.soul4723.extrapermissions.command.GameruleCommandHandler;
import io.github.soul4723.extrapermissions.command.TimeCommandHandler;
import io.github.soul4723.extrapermissions.command.WeatherCommandHandler;
import io.github.soul4723.extrapermissions.command.WhitelistCommandHandler;
import io.github.soul4723.extrapermissions.listener.AdminBroadcastListener;
import io.github.soul4723.extrapermissions.listener.ChatSpeedBypassListener;
import io.github.soul4723.extrapermissions.listener.DebugStickListener;
import io.github.soul4723.extrapermissions.listener.ForceGamemodeBypassListener;
import io.github.soul4723.extrapermissions.listener.MoveSpeedBypassListener;
import io.github.soul4723.extrapermissions.listener.OperatorBlockListener;
import io.github.soul4723.extrapermissions.listener.SelectorPermissionListener;
import io.github.soul4723.extrapermissions.listener.SpawnProtectionListener;
import io.github.soul4723.extrapermissions.listener.WhitelistBypassListener;
import io.github.soul4723.extrapermissions.util.LuckPermsHook;
import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ExtraPermissions extends JavaPlugin {
    
    private LuckPermsHook luckPermsHook;
    
    private boolean checkDependencies() {
        boolean hasAll = true;
        
        if (getServer().getPluginManager().getPlugin("CommandAPI") == null) {
            getLogger().severe("ExtraPermissions requires CommandAPI to function!");
            getLogger().severe("Download: https://github.com/JorelAli/CommandAPI/releases");
            hasAll = false;
        }
        
        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().severe("ExtraPermissions requires LuckPerms to function!");
            getLogger().severe("Download: https://luckperms.net/download");
            hasAll = false;
        }
        
        return hasAll;
    }
    
    @Override
    public void onLoad() {
        CommandAPI.onLoad(new CommandAPIBukkitConfig(this));
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
        
        PermissionManager.initialize(luckPermsHook);

        CommandAPI.onEnable();

        ExtraPermissionsCommand.registerCommands(this);
        registerGranularCommands();
        registerListeners();
        
        getLogger().info("ExtraPermissions v" + getDescription().getVersion() + " enabled (inspired by VanillaPermissions)");
    }
    
    private void registerGranularCommands() {
        if (isFeatureEnabled("command_permissions")) {
            try {
                WhitelistCommandHandler.registerCommands();
                TimeCommandHandler.registerCommands();
                WeatherCommandHandler.registerCommands();
                GameruleCommandHandler.registerCommands();
                DifficultyCommandHandler.registerCommands();
                EffectCommandHandler.registerCommands();
                getLogger().info("Granular commands registered successfully");
            } catch (Exception e) {
                getLogger().warning("Failed to register granular commands: " + e.getMessage());
            }
        }
    }
    
    private void registerListeners() {
        if (isFeatureEnabled("admin_permissions")) {
            getServer().getPluginManager().registerEvents(new AdminBroadcastListener(), this);
            getServer().getPluginManager().registerEvents(new DebugStickListener(), this);
            getServer().getPluginManager().registerEvents(new OperatorBlockListener(), this);
        }
        
        if (isFeatureEnabled("bypass_permissions")) {
            getServer().getPluginManager().registerEvents(new ChatSpeedBypassListener(), this);
            getServer().getPluginManager().registerEvents(new SpawnProtectionListener(), this);
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
        
        return true;
    }
    
    public void reloadPluginConfig() {
        reloadConfig();
        PermissionManager.clearCache();
        getLogger().info("Configuration reloaded - restart plugin for full effect");
    }
    
    @Override
    public void onDisable() {
        PermissionManager.close();
        CommandAPI.onDisable();
        getLogger().info("ExtraPermissions disabled");
    }
}
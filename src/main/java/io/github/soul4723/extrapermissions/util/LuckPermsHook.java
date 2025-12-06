package io.github.soul4723.extrapermissions.util;

import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public class LuckPermsHook {
    private LuckPerms luckPerms;
    private boolean enabled = false;
    
    public void initialize(Plugin plugin) {
        Plugin luckPermsPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (luckPermsPlugin != null) {
            try {
                RegisteredServiceProvider<LuckPerms> provider = 
                    Bukkit.getServicesManager().getRegistration(LuckPerms.class);
                
                if (provider != null) {
                    this.luckPerms = provider.getProvider();
                    this.enabled = true;
                    plugin.getLogger().info("LuckPerms integration enabled!");
                } else {
                    plugin.getLogger().warning("LuckPerms found but service provider not available");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to initialize LuckPerms: " + e.getMessage());
            }
        } else {
            plugin.getLogger().warning("LuckPerms not found - plugin will not function");
        }
    }
    
    public boolean isEnabled() {
        return enabled && luckPerms != null;
    }
    
    public LuckPerms getLuckPerms() {
        return luckPerms;
    }
}
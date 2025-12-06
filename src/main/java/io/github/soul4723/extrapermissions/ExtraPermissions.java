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

        ExtraPermissionsCommand.registerCommands();

        getServer().getPluginManager().registerEvents(new AdminBroadcastListener(), this);
        getServer().getPluginManager().registerEvents(new ChatSpeedBypassListener(), this);
        
        getServer().getPluginManager().registerEvents(new DebugStickListener(), this);
        getServer().getPluginManager().registerEvents(new MovementSpeedBypassListener(), this);
        getServer().getPluginManager().registerEvents(new WhitelistBypassListener(), this);
        getServer().getPluginManager().registerEvents(new OperatorBlockListener(), this);
        getServer().getPluginManager().registerEvents(new NBTPermissionListener(), this);
        getServer().getPluginManager().registerEvents(new SelectorPermissionListener(), this);
        
        getLogger().info("ExtraPermissions v2.0.0 enabled with LuckPerms integration");
    }
    
    @Override
    public void onDisable() {
        CommandAPI.onDisable();
        getLogger().info("ExtraPermissions disabled");
    }
}
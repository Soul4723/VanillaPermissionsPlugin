package io.github.soul4723.vanillapermissions;

import io.github.soul4723.vanillapermissions.command.VanillaPermissionsPluginCommand;
import io.github.soul4723.vanillapermissions.listener.DebugStickListener;
import io.github.soul4723.vanillapermissions.listener.ForceGamemodeBypassListener;
import io.github.soul4723.vanillapermissions.listener.LuckPermsPreLoginListener;
import io.github.soul4723.vanillapermissions.listener.MoveSpeedBypassListener;
import io.github.soul4723.vanillapermissions.listener.OperatorBlockListener;
import io.github.soul4723.vanillapermissions.listener.ProviderReselectListener;
import io.github.soul4723.vanillapermissions.listener.WhitelistBypassListener;
import io.github.soul4723.vanillapermissions.util.LuckPermsPermissionProvider;
import io.github.soul4723.vanillapermissions.util.NativeCommandPermissions;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import io.github.soul4723.vanillapermissions.util.PermissionProvider;
import io.github.soul4723.vanillapermissions.util.SupportedVersions;
import org.bukkit.plugin.java.JavaPlugin;

public class VanillaPermissionsPlugin extends JavaPlugin {

    private PermissionProvider permissionProvider;

    @Override
    public void onEnable() {
        String minecraftVersion = getServer().getMinecraftVersion();
        if (!SupportedVersions.isVerified(minecraftVersion)) {
            getLogger().warning("This server runs Minecraft " + minecraftVersion + ", which VanillaPermissionsPlugin has not been tested on "
                + "(tested: Paper " + String.join(", ", SupportedVersions.VERIFIED) + "). If the vanilla command checks cannot be added, "
                + "it disables itself and leaves vanilla commands unchanged");
        }

        // Chosen now because the commands below need a provider at once; ProviderReselectListener repeats the choice
        // on ServerLoadEvent, which fires after every plugin has enabled.
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
        getServer().getPluginManager().registerEvents(new ProviderReselectListener(this), this);
        getServer().getPluginManager().registerEvents(new DebugStickListener(), this);
        getServer().getPluginManager().registerEvents(new OperatorBlockListener(), this);
        getServer().getPluginManager().registerEvents(new WhitelistBypassListener(), this);
        getServer().getPluginManager().registerEvents(new ForceGamemodeBypassListener(), this);
        getServer().getPluginManager().registerEvents(new MoveSpeedBypassListener(), this);
    }

    @Override
    public void onDisable() {
        PermissionManager.getProvider().close();
        NativeCommandPermissions.uninstall();
    }
}

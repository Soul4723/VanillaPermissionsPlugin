package io.github.soul4723.vanillapermissions;

import io.github.soul4723.vanillapermissions.command.VanillaPermissionsPluginCommand;
import io.github.soul4723.vanillapermissions.listener.LuckPermsPreLoginListener;
import io.github.soul4723.vanillapermissions.listener.WhitelistBypassListener;
import io.github.soul4723.vanillapermissions.platform.AdapterLog;
import io.github.soul4723.vanillapermissions.platform.Adapters;
import io.github.soul4723.vanillapermissions.platform.ServerVersion;
import io.github.soul4723.vanillapermissions.platform.VersionAdapter;
import io.github.soul4723.vanillapermissions.util.LuckPermsPermissionProvider;
import io.github.soul4723.vanillapermissions.util.PermissionManager;
import io.github.soul4723.vanillapermissions.util.PermissionProvider;
import io.github.soul4723.vanillapermissions.util.ServerSoftware;
import io.github.soul4723.vanillapermissions.util.SupportedVersions;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class VanillaPermissionsPlugin extends JavaPlugin {

    private PermissionProvider permissionProvider;
    private VersionAdapter adapter;

    @Override
    public void onEnable() {
        String minecraftVersion = ServerVersion.minecraft();
        ServerSoftware software = ServerSoftware.detect();
        if (!SupportedVersions.isVerified(software, minecraftVersion)) {
            getLogger().warning("This server runs Minecraft " + minecraftVersion + ", which VanillaPermissionsPlugin has not been tested on "
                + "(tested: " + SupportedVersions.describe(software) + "). If the vanilla command checks cannot be added, "
                + "it disables itself and leaves vanilla commands unchanged");
        }

        adapter = Adapters.select(getClassLoader(), getLogger());
        if (adapter == null) {
            String reason = software == ServerSoftware.FOLIA ? "Folia is supported from 1.20.6"
                : !Adapters.isCraftBukkit(getServer()) ? "not a CraftBukkit-based server"
                : "none of the ways this plugin adds its checks fits it; see the warnings above";
            getLogger().severe(software.displayName() + " " + minecraftVersion + " is not supported (" + reason + "), so "
                + "VanillaPermissionsPlugin is disabling itself and leaving vanilla commands unchanged; supported servers are "
                + "listed in docs/compatibility.md");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info(AdapterLog.using(adapter.id(), software.displayName(), minecraftVersion));

        // Chosen now because the commands below need a provider at once; the adapter's listeners repeat the choice
        // on ServerLoadEvent, which fires after every plugin has enabled.
        permissionProvider = PermissionManager.selectProvider(this);
        PermissionManager.initialize(permissionProvider, this);

        VanillaPermissionsPluginCommand.register(this);
        registerListeners();
        try {
            adapter.enable(this);
        } catch (Exception | LinkageError error) {
            // An Error left unhandled would leave the plugin enabled, with its listeners but no checks.
            getLogger().log(Level.SEVERE, AdapterLog.installFailed(software.displayName(), minecraftVersion,
                SupportedVersions.describe(software)), error);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerListeners() {
        if (permissionProvider instanceof LuckPermsPermissionProvider) {
            LuckPermsPermissionProvider luckPerms = (LuckPermsPermissionProvider) permissionProvider;
            getServer().getPluginManager().registerEvents(
                new LuckPermsPreLoginListener(luckPerms.getHook().getLuckPerms(), getLogger()), this);
        }
        getServer().getPluginManager().registerEvents(new WhitelistBypassListener(), this);
    }

    @Override
    public void onDisable() {
        disable(adapter, PermissionManager.getProvider());
    }

    static void disable(VersionAdapter adapter, PermissionProvider provider) {
        try {
            if (adapter != null) adapter.disable();
        } finally {
            provider.close();
        }
    }

    /** The adapter chosen in {@link #onEnable}, or {@code null} if this server is not supported. */
    public VersionAdapter getVersionAdapter() {
        return adapter;
    }
}

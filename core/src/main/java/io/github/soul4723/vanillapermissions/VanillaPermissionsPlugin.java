package io.github.soul4723.vanillapermissions;

import io.github.soul4723.vanillapermissions.command.VanillaPermissionsPluginCommand;
import io.github.soul4723.vanillapermissions.listener.LuckPermsPreLoginListener;
import io.github.soul4723.vanillapermissions.listener.WhitelistBypassListener;
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
            getLogger().severe(getServer().getName() + " " + minecraftVersion + " is not supported yet, so VanillaPermissionsPlugin "
                + "is disabling itself and leaving vanilla commands unchanged; "
                + (software == ServerSoftware.FOLIA ? "Folia is supported from 1.20.6; " : "")
                + "supported servers are listed in docs/compatibility.md");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Chosen now because the commands below need a provider at once; the adapter's listeners repeat the choice
        // on ServerLoadEvent, which fires after every plugin has enabled.
        permissionProvider = PermissionManager.selectProvider(this, adapter.scheduler(this));
        PermissionManager.initialize(permissionProvider, this);

        VanillaPermissionsPluginCommand.register(this);
        registerListeners();
        try {
            adapter.enable(this);
        } catch (Exception error) {
            getLogger().log(Level.SEVERE, "Could not enable the " + adapter.id() + " adapter, so VanillaPermissionsPlugin is "
                + "disabling itself and leaving vanilla commands unchanged", error);
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

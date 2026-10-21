# Roadmap

The long-term scope is any Minecraft version or server software where a permissions plugin alone does not give granular control over vanilla behaviour. Today that is the Bukkit family (Paper, Folia, Spigot and their forks) from 1.8.8 to 26.3; see [Compatibility](compatibility.md). Nothing below is promised or scheduled.

| Target | Status | Notes |
| --- | --- | --- |
| Sub-nodes for more commands on 1.8.8 – 1.12.2 | not planned | Ten commands have sub-nodes; the rest keep `minecraft.command.<name>`. See [Permissions](permissions.md#188---1122-legacy-servers). |
| Fabric, Quilt | deferred | [DrexHD/VanillaPermissions](https://github.com/DrexHD/VanillaPermissions) already covers these. |
| NeoForge, Forge | deferred | NeoForge's PermissionAPI has no "unset" for dynamic nodes, so a module would call LuckPerms directly or use a fabric-permissions-api-style provider. |
| Sponge | research | Check whether Sponge's native permissions already give granular vanilla control before building anything. |

# Troubleshooting and limitations

## FAQ

- **I granted a command but the player still can't use part of it.** Run `/vanillapermissions check <player> <node>` for the node you expect, or `/lp verbose on <player> & minecraft.command.<command>` (then `/lp verbose off`), and look for a `false` on a child node, or one inherited from a group. `@` selectors also need `minecraft.selector`, and the selector scope nodes must not be `false`.
- **The player can do more than I wanted.** Granting a root grants every sub-command and target. Set the unwanted branches or selector scopes to `false`.
- **An operator lost access.** An explicit `false` beats operator status. Look for the node in the operator's own and inherited groups, including wildcards.
- **The plugin disabled itself at startup.** The log says why; see [Reading the startup log](#reading-the-startup-log). Usually the server is not a supported version (see [Compatibility](compatibility.md)), or something removed a vanilla command.
- **`/minecraft:<command>` behaves differently from `/<command>`.** Another plugin owns the plain name; see below.
- **A change did not reach the player's command list.** LuckPerms re-sends the list itself after a change (its `update-client-command-list` option, on by default). If you turned that option off, tab completion updates when the player rejoins. With other plugins the list refreshes on rejoin, on an operator change, or when the plugin resends it; see [Permissions plugins](providers.md).
- **A bypass node does nothing.** Bypass nodes are grant-only and default to `false`; check with `/vanillapermissions check <player> <node>`. For the whitelist and player-limit bypasses with a non-LuckPerms plugin, see [Permissions plugins](providers.md#other-permissions-plugins).

## Reading the startup log

The plugin logs a few lines as it starts. On Paper 1.21.11 with LuckPerms they look like this (`<n>` is the number of command nodes, which depends on the version):

```text
Using Paper mode on Paper 1.21.11 (checks on every vanilla command and sub-command)
Using LuckPerms <version> for permission checks, ...
Installed vanilla /execute permission, feedback, and oplevel extensions
Added permission checks to <n> vanilla command nodes
```

- **The mode line** says how the plugin adds its checks: `Paper mode` (Paper, Folia, Purpur, Pufferfish 1.20.6 and later), `Spigot mode` (Spigot 1.13 and later, and Paper 1.13 – 1.20.5, for example `Using Spigot mode on Paper 1.16.5`) or `legacy mode` (1.8.8 – 1.12.2).
- **On 1.8.8 – 1.12.2** the mode line reads `Using legacy mode on Spigot 1.12.2 (sub-command checks for 10 commands only; other vanilla commands keep their root node)`. There is no `/execute` line, `Added permission checks to <n>` appears one tick after startup, after the server's `Done (...)!` line, and `<n>` is small (over a hundred, not over seven hundred as on 1.13 and later).
- **On Spigot** an INFO line lists the bypass nodes that need Paper's API and do nothing there (see [Compatibility](compatibility.md#limits)).
- `/vanillapermissions debug` repeats the mode, the server version, the permissions plugin and the node count.

Problems look like this:

- `Could not add permission checks to vanilla commands on <software> <version> (tested: ...), so VanillaPermissionsPlugin is disabling itself and vanilla commands work as they would without it.` (SEVERE) The server is a version the plugin could not patch. Vanilla commands keep working as if the plugin were not installed. If the version is in the [supported list](compatibility.md), report it with the full startup log.
- `<software> <version> is not supported (<reason>), so VanillaPermissionsPlugin is disabling itself and leaving vanilla commands unchanged` (SEVERE) The software or version is not supported, for example `Folia is supported from 1.20.6` or `not a CraftBukkit-based server`.
- `No permissions plugin found, so every node follows Bukkit defaults and operator status; install a permissions plugin such as LuckPerms for granular control` (WARNING) The checks are installed but nobody can be given a node. Install LuckPerms, or another permissions plugin; see [Permissions plugins](providers.md).
- `Selected after all plugins enabled: ...` means a permissions plugin enabled after this one and was picked up. It is not a problem.

## Commands from other plugins

Only vanilla commands get `minecraft.command.*` checks. Bukkit and plugin commands (`/lp`, Bukkit's `/help`, `/version`, EssentialsX…) keep their own permissions, even under `minecraft.command.*`.

If a plugin takes over a vanilla name (for example EssentialsX `/gamemode` or `/time`), the plugin's version uses the plugin's permissions. The vanilla version stays reachable as `/minecraft:gamemode` with the normal `minecraft.command.gamemode…` nodes.

## Limitations

The limits are listed once, in [Compatibility](compatibility.md#limits): unsupported and untested versions, features missing on some servers, runtime enabling and disabling, and the extra limits on 1.8.8 - 1.12.2. Notes on Folia are in [Compatibility](compatibility.md#folia).

Reporting a problem: use the [issue templates](https://github.com/Soul4723/VanillaPermissionsPlugin/issues/new/choose) and include the output of `/vanillapermissions debug` (and `/vanillapermissions check` for the node involved).

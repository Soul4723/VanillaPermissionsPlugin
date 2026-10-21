# Troubleshooting and limitations

## FAQ

- **I granted a command but the player still can't use part of it.** Run `/lp verbose on <player>` and look for a `false` on a child node, or one inherited from a group. `@` selectors also need `minecraft.selector`, and the selector scope nodes must not be `false`.
- **The player can do more than I wanted.** Granting a root grants every sub-command and target. Set the unwanted branches or selector scopes to `false`.
- **An operator lost access.** An explicit `false` beats operator status. Look for the node in the operator's own and inherited groups, including wildcards.
- **The plugin disabled itself at startup.** The log says why. Usually the server is not a supported version (Spigot 1.21.11 and 26.x are not; see [Compatibility](compatibility.md#limits)), or something removed a vanilla command.
- **`/minecraft:<command>` behaves differently from `/<command>`.** Another plugin owns the plain name; see below.
- **A change did not reach the player's command list.** With LuckPerms the list is re-sent automatically. With other plugins it refreshes on rejoin, on an operator change, or when the plugin resends it; see [Permissions plugins](providers.md).
- **A bypass node does nothing.** Bypass nodes are grant-only and default to `false`; check with `/vanillapermissions check <player> <node>`. For the whitelist and player-limit bypasses with a non-LuckPerms plugin, see [Permissions plugins](providers.md#other-permissions-plugins).

## Commands from other plugins

Only vanilla commands get `minecraft.command.*` checks. Bukkit and plugin commands (`/lp`, Bukkit's `/help`, `/version`, EssentialsX…) keep their own permissions, even under `minecraft.command.*`.

If a plugin takes over a vanilla name (for example EssentialsX `/gamemode` or `/time`), the plugin's version uses the plugin's permissions. The vanilla version stays reachable as `/minecraft:gamemode` with the normal `minecraft.command.gamemode…` nodes.

## Limitations

The limits are listed once, in [Compatibility](compatibility.md#limits): unsupported and untested versions, features missing on some servers, and the extra limits on 1.8.8 - 1.12.2 and 1.13 - 1.16. Notes on Folia are in [Compatibility](compatibility.md#folia).

- **Disabling at runtime:** if a plugin manager disables this plugin while the server keeps running (Spigot and Paper before 1.20.6), the plugin re-syncs commands to remove its checks from the `minecraft:` copies. It skips the re-sync only when the server is known to be stopping; an unavailable shutdown probe does not prevent the re-sync. Enabling or disabling the plugin while players are online may race the server's asynchronous command-tree send and cause a command-list update to fail.
- With `/execute feedback console` the console may show a result twice (the direct output plus the admin log line).

Reporting a problem: use the [issue templates](https://github.com/Soul4723/VanillaPermissionsPlugin/issues/new/choose) and include the output of `/vanillapermissions debug` (and `/vanillapermissions check` for the node involved).

# Troubleshooting

## FAQ

- **I granted a command but the player still can't use part of it.** Run `/lp verbose on <player>` and look for a `false` on a child node, or one inherited from a group. `@` selectors also need `minecraft.selector`, and the selector scope nodes must not be `false`.
- **The player can do more than I wanted.** Granting a root grants every sub-command and target. Set the unwanted branches or selector scopes to `false`.
- **An operator lost access.** An explicit `false` beats operator status. Look for the node in the operator's own and inherited groups, including wildcards.
- **The plugin disabled itself at startup.** The log says why. Usually the server is not a supported Paper version (see [Compatibility](compatibility.md)), or something removed a vanilla command.
- **`/minecraft:<command>` behaves differently from `/<command>`.** Another plugin owns the plain name; see below.
- **A change did not reach the player's command list.** With LuckPerms the list is re-sent automatically. With other plugins it refreshes on rejoin, on an operator change, or when the plugin resends it; see [Permission plugins](providers.md).
- **A bypass node does nothing.** Bypass nodes are grant-only and default to `false`; check with `/vanillapermissions check <player> <node>`. For the whitelist and player-limit bypasses with a non-LuckPerms plugin, see [Permission plugins](providers.md#other-permissions-plugins).

## Plugin commands from other plugins

Only vanilla commands get `minecraft.command.*` checks. Bukkit and plugin commands (`/lp`, Bukkit's `/help`, `/version`, EssentialsX…) keep their own permissions, even under `minecraft.command.*`.

If a plugin takes over a vanilla name (for example EssentialsX `/gamemode` or `/time`), the plugin's version uses the plugin's permissions. The vanilla version stays reachable as `/minecraft:gamemode` with the normal `minecraft.command.gamemode…` nodes.

## Limitations

- Paper 1.20.6 through 26.3 only; see [Compatibility](compatibility.md). On other versions the plugin logs a warning, and disables itself if the command tree cannot be patched.
- Player-level checks on 26.3 are pending a test client.
- Operator block and debug stick nodes can only take access away.
- Upstream nodes this plugin does not add, because Paper has no API for them: `minecraft.bypass.spawn-protection`, `minecraft.bypass.chat-speed`, `minecraft.bypass.move-speed.vehicle.<entity>`, `minecraft.debug_chart`, `minecraft.<query|load>.<entity|block>`, `minecraft.adminbroadcast.receive` (use Paper's `minecraft.admin.command_feedback`) and the operator block `edit` action.
- Selector weight is only compared for targets whose LuckPerms data is loaded (normally online players).
- With `/execute feedback console` the console may show a result twice (the direct output plus the admin log line).

Reporting a problem: use the [issue templates](https://github.com/Soul4723/VanillaPermissionsPlugin/issues/new/choose) and include the output of `/vanillapermissions check`.

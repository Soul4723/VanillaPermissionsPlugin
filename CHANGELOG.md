# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2026-10-29

First public release: a Bukkit port of [DrexHD's VanillaPermissions](https://github.com/DrexHD/VanillaPermissions) for Fabric. One jar runs on Paper, Folia, Spigot, Purpur and Pufferfish from Minecraft 1.8.8 to 26.3. There is no config file; everything is set through your permissions plugin.

### Added
- Permission nodes for every part of the vanilla command tree (`minecraft.command.…`), including one node per game mode, and the same nodes for `/minecraft:<command>` aliases.
- Selector control: `minecraft.selector` for `@` syntax, scope nodes for targeting yourself, other players or entities, and limit and weight meta. Limit and weight keys fall back to shorter forms: `minecraft.selector.limit.effect.targets` caps every form of `/effect`, `minecraft.selector.limit.effect.targets.give` only `/effect give`, and the longer key wins. Random selectors (`@r`, `sort=random`) are checked against every entity they could pick, because the server picks again when the command runs. Paper's own selector node is granted through `minecraft.selector`.
- Upstream's `/execute` extensions: `if|unless permission`, `feedback` and `oplevel`.
- Bypass nodes for the whitelist, the player limit, forced game mode and the movement checks. They default to false, so operators follow vanilla unless granted. The force-gamemode and move-speed bypasses need Paper (or 1.8.8 - 1.12.2) and do nothing on Spigot 1.13 and later.
- Deny nodes for command blocks, structure blocks, jigsaws, and the 1.21.5+ test blocks and test instance blocks, and a per-block debug stick node (`minecraft.debug_stick.use.<namespace>.<block>`).
- Supported servers: Paper and Spigot 1.8.8 - 26.3, Folia and Purpur 1.20.6 - 26.3, Pufferfish 1.21 - 1.21.10. The jar runs on Java 8 and later; Paper and its forks from 1.20.6 need Java 21 or later, as those servers do. Folia before 1.20.6 is refused with a clear message. A warning is logged once at startup on a version outside the tested list. See [Compatibility](docs/compatibility.md).
- On 1.8.8 - 1.12.2, which have no command tree, sub-nodes exist for ten commands (`gamemode`, `time`, `weather`, `kill`, `effect`, `give`, `clear`, `tp`/`teleport`, `execute`, `scoreboard`) and are checked before the command runs, with selector rules, tab completion filtering (1.9+), operator blocks and the bypasses. Other commands keep `minecraft.command.<name>`. See [Permissions](docs/permissions.md#188---1122-legacy-servers).
- LuckPerms is optional. Any Bukkit permissions plugin works for nodes, and Vault supplies selector limits and weights and the login bypasses. A permissions plugin that loads after this one is still picked up. With no permissions plugin the plugin runs on Bukkit defaults and logs a warning; a LuckPerms that failed to start falls back to Bukkit permissions.
- Tab completion follows LuckPerms changes through LuckPerms' `update-client-command-list` option, on by default. With it off, a player sees the change after rejoining.
- `/vanillapermissions check` shows how a node resolves for a player and says when a node has no effect on this server. `/vanillapermissions debug` shows the mode, server version, whether that version was tested, the permissions plugin and the number of checked nodes.
- The startup log names the mode in use, for example `Using Paper mode on Paper 1.21.11 (...)`. If the command tree cannot be patched, the plugin logs why, restores the vanilla commands and disables itself. On Spigot 1.13 and later, a vanilla command that another plugin replaced is left unchecked and logged as a warning.
- Documentation under [`docs/`](docs/README.md), and releases published with a SHA-256 checksum.

### Security
- Denied commands and branches are removed from the command list and from tab completion, and are refused when typed.
- `/execute feedback entity` and `/execute oplevel` never raise the caller's permissions: command and selector nodes stay checked against the original player.
- Checks fail closed: on 1.8.8 - 1.12.2 a command whose check fails with an error is refused instead of running unchecked, and selector rules refuse an entity they cannot check.

[Unreleased]: https://github.com/Soul4723/VanillaPermissionsPlugin/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Soul4723/VanillaPermissionsPlugin/releases/tag/v1.0.0

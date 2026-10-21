# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Upgrading from 1.0.0

Replace the jar: stop the server, swap `VanillaPermissionsPlugin-1.0.0.jar` for the new one in `plugins/`, and start it. There is no config file and the node names are unchanged. On Paper 1.20.6 and later the behaviour is the same as in 1.0.0 except for the changes listed below; in particular, shorter selector meta keys now apply, and random selectors are checked more strictly.

### Added
- Spigot, old Paper, Purpur, Pufferfish and Folia are supported by the same jar, from Minecraft 1.8.8 to 26.3. The plugin picks one of three modes at startup by looking at the server: Paper mode (Paper, Folia and forks, 1.20.6 and later), Spigot mode (Spigot 1.13+ and Paper 1.13 - 1.20.5) and legacy mode (1.8.8 - 1.12.2). See [Compatibility](docs/compatibility.md) for the version table, the limits and what was tested.
- Folia 1.20.6 and later, in Paper mode: LuckPerms refreshes run on each player's entity scheduler and global work on the global scheduler. `plugin.yml` declares `folia-supported: true`. Older Folia is refused with a clear message.
- On 1.8.8 - 1.12.2 the modern sub-nodes for ten commands (`gamemode`, `time`, `weather`, `kill`, `effect`, `give`, `clear`, `tp`/`teleport`, `execute`, `scoreboard`) are checked before the command runs, with selector rules, tab completion filtering (1.9+), operator blocks, and the force-gamemode and move-speed bypasses. Other commands keep `minecraft.command.<name>`; `/tp` follows `minecraft.command.teleport` as on 1.13+. See [Permissions](docs/permissions.md#188---1122-legacy-servers).
- The startup warning and `/vanillapermissions debug` compare the server with the tested versions of its own software (Paper, Folia, Spigot, Purpur or Pufferfish).
- Limit and weight meta now fall back to shorter keys. `minecraft.selector.limit.effect.targets` caps every form of `/effect`, while `minecraft.selector.limit.effect.targets.give` caps only `/effect give`. Keys written in the full form keep working and win over shorter ones.
- The startup log names the mode in use (for example `Using Paper mode on Paper 1.21.11 (...)`), and `/vanillapermissions debug` has a `Mode:` line and shows the detected server software and version.

### Changed
- `plugin.yml` declares `api-version: '1.13'` (it was `'1.20.6'`), the lowest value every 1.13+ server accepts.
- The jar now runs on Java 8 and later; the Paper 1.20.6+ part only loads on Java 21 and later, as those servers need. The manifest no longer claims Java 21.
- Development builds are `1.1.0-SNAPSHOT`; the release jar is still `VanillaPermissionsPlugin-<version>.jar`.

### Fixed
- Random selectors (`@r`, or any selector with `sort=random`) are checked against every entity they could pick, because the server picks again when the command runs. A weight rule now denies `/kill @r` if any possible target outranks the sender.
- The plugin no longer resends command lists itself when LuckPerms permissions change. LuckPerms already does this (its `update-client-command-list` option, on by default). If you turned that option off, tab completion updates when the player rejoins.
- The failure messages at startup say what went wrong and where to find the supported servers.
- Console and command blocks no longer fail on `/scoreboard players … *` and on `/execute unless entity …` with an empty selection. Selector validation now skips non-player sources, and player checks keep vanilla's wildcard score holders (`*`) and empty selections. This also fixes the behaviour shipped on Paper in 1.0.0.
- Paper 1.20.5 no longer refuses the plugin because of its `api-version`.
- The operator-block nodes now also cover the 1.21.5+ test blocks and test instance blocks.
- LuckPerms refreshes no longer iterate the live online-player list from LuckPerms' event thread, which could lose a refresh when a player joined or left.
- On disable, the permission checks are removed before the permissions provider is closed, so a provider that fails to close cannot leave them installed.

## [1.0.0] - 2026-10-20

First public release: a drop-in Paper port of [DrexHD's VanillaPermissions](https://github.com/DrexHD/VanillaPermissions).

### Added
- Permission nodes for every part of the vanilla command tree (`minecraft.command.…`), including one node per game mode, and the same nodes for `/minecraft:<command>` aliases.
- Selector control: `minecraft.selector` for `@` syntax, scope nodes for targeting yourself, other players or entities, and limit and weight meta. Paper's own selector node is granted through `minecraft.selector`.
- Upstream's `/execute` extensions: `if|unless permission`, `feedback` and `oplevel`.
- Bypass nodes for the whitelist, the player limit, forced game mode and the movement checks. They default to false, so operators follow vanilla unless granted.
- Deny nodes for command blocks, structure blocks and jigsaws, and a per-block debug stick node (`minecraft.debug_stick.use.<namespace>.<block>`).
- Support for Paper 1.20.6 through 26.3 with one jar (Java 21 for 1.20.6 to 1.21.11, Java 25 for 26.x). A warning is logged once at startup on versions outside the tested list.
- LuckPerms is optional. Any Bukkit permissions plugin works for nodes, and Vault supplies selector limits and weights and the login bypasses. With no permissions plugin the plugin runs on Bukkit defaults and logs a warning.
- The permissions source is chosen again once every plugin has enabled, so a permissions plugin that loads after this one is still picked up, with Vault meta, and the no-permissions-plugin warning is logged once, after that final choice.
- `/vanillapermissions check` and `debug` to see how a node resolves and which permissions source is in use.
- Documentation under `docs/`, a Paper boot check for every supported version in CI, and tagged releases published with a SHA-256 checksum.

### Fixed
- Selector permissions are checked against the full command path after redirects, and denied selections change nothing.
- A LuckPerms that is installed but failed to start no longer crashes the plugin; it falls back to Bukkit permissions.
- The plugin disables itself safely, restoring the vanilla commands, if the command tree cannot be patched.

### Security
- Denied commands and branches are removed from the command list and from tab completion, and are refused when typed.
- `/execute feedback entity` and `/execute oplevel` never raise the caller's permissions: command and selector nodes stay checked against the original player.

[Unreleased]: https://github.com/Soul4723/VanillaPermissionsPlugin/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Soul4723/VanillaPermissionsPlugin/releases/tag/v1.0.0

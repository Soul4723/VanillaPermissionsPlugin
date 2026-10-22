# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Replace the jar to upgrade from 1.0.0; there is no config file and node names are unchanged. On Paper 1.20.6 and later behaviour is the same as in 1.0.0 except for the changes below. The most visible ones are that shorter selector meta keys now apply and that random selectors are checked more strictly.

### Added
- Support for Spigot, older Paper, Purpur, Pufferfish and Folia with the same jar, from Minecraft 1.8.8 to 26.3. The plugin picks a mode at startup by looking at the server: Paper mode (Paper, Folia and forks, 1.20.6 and later), Spigot mode (Spigot 1.13+ and Paper 1.13 - 1.20.5) or legacy mode (1.8.8 - 1.12.2). See [Compatibility](docs/compatibility.md) for the version tables and limits.
- Folia 1.20.6 and later is supported. Older Folia is refused with a clear message.
- On 1.8.8 - 1.12.2 the modern sub-nodes for ten commands (`gamemode`, `time`, `weather`, `kill`, `effect`, `give`, `clear`, `tp`/`teleport`, `execute`, `scoreboard`) are checked before the command runs, with selector rules, tab completion filtering (1.9+), operator blocks, and the force-gamemode and move-speed bypasses. Other commands keep `minecraft.command.<name>`. See [Permissions](docs/permissions.md#188---1122-legacy-servers).
- Selector limit and weight meta fall back to shorter keys. `minecraft.selector.limit.effect.targets` caps every form of `/effect`, while `minecraft.selector.limit.effect.targets.give` caps only `/effect give`. Keys written in the full form keep working and win over shorter ones.
- The startup log names the mode in use (for example `Using Paper mode on Paper 1.21.11 (...)`), and `/vanillapermissions debug` shows a `Mode:` line and whether your server software and version were tested.
- Operator-block nodes also cover the 1.21.5+ test blocks and test instance blocks.

### Changed
- The jar runs on Java 8 and later; only the Paper 1.20.6+ part needs Java 21 or later, as those servers do.
- `plugin.yml` declares `api-version: '1.13'` (it was `'1.20.6'`), the lowest value every 1.13+ server accepts. Paper 1.20.5 no longer refuses the plugin because of it.
- The plugin no longer resends command lists when LuckPerms permissions change. LuckPerms already does this (its `update-client-command-list` option, on by default). If you turned that option off, tab completion updates when the player rejoins.
- Startup failure messages say what went wrong and where to find the supported servers. `/vanillapermissions check` says when a node has no effect on this server, and an unknown subcommand is reported instead of silently showing the help.
- On Spigot 1.13+, a vanilla command that another plugin changed (and that is therefore left unchecked) is logged as a warning.

### Fixed
- Random selectors (`@r`, or any selector with `sort=random`) are checked against every entity they could pick, because the server picks again when the command runs. A weight rule now denies `/kill @r` if any possible target outranks the sender.
- Console and command blocks no longer fail on `/scoreboard players ... *` and on `/execute unless entity ...` with an empty selection. Player checks keep vanilla's wildcard score holders (`*`) and empty selections. This also fixes the behaviour shipped on Paper in 1.0.0.
- On 1.8.8 - 1.12.2 a command whose check fails with an error is refused instead of running unchecked, and tab completion for the command an `/execute` runs is filtered too.
- From Minecraft 1.21.9, `/op`, `/deop`, `/ban`, `/pardon` and `/whitelist add|remove` are no longer refused for players with "This target cannot be checked against selector permissions"; their profile targets are checked as players. This also fixes the behaviour shipped on Paper in 1.0.0.
- Selector rules can no longer be skipped when the second lookup of the command sender fails; the first lookup is used. This also fixes the behaviour shipped on Paper in 1.0.0.
- An entity that CraftBukkit cannot wrap is refused by selector rules instead of being treated as a score-holder name and skipped. This also fixes the behaviour shipped on Paper in 1.0.0.

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

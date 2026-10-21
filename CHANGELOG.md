# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Spigot, old Paper, Purpur, Pufferfish and Folia are supported by the same jar, from Minecraft 1.8.8 to 26.3. The plugin picks one of three adapters at startup by probing the server: `paper` (Paper, Folia and forks, 1.20.6 and later), `spigot` (Brigadier on Spigot 1.13+ and Paper 1.13 - 1.20.5) and `legacy` (1.8.8 - 1.12.2). See [Compatibility](docs/compatibility.md) for the version table, the limits and what was tested.
- Folia 1.20.6 and later, through the `paper` adapter: LuckPerms refreshes run on each player's entity scheduler and global work on the global scheduler. `plugin.yml` declares `folia-supported: true`. Older Folia is refused instead of falling through to the `spigot` adapter.
- On 1.8.8 - 1.12.2 the modern sub-nodes for ten commands (`gamemode`, `time`, `weather`, `kill`, `effect`, `give`, `clear`, `tp`/`teleport`, `execute`, `scoreboard`) are checked before the command runs, with selector rules, tab completion filtering (1.9+), operator blocks, and the force-gamemode and move-speed bypasses. Other commands keep `minecraft.command.<name>`. See [Permissions](docs/permissions.md#188---1122-legacy-servers).
- The startup warning and `/vanillapermissions debug` compare the server with the tested versions of its own software (Paper, Folia, Spigot, Purpur or Pufferfish).
- Compatibility workflow rows for Spigot and old Paper (1.8.8 - 1.20.5, Java 8 to 21), Folia, Purpur and Pufferfish, and the live-test harness (`scripts/live-test`). `scripts/compat-smoke.sh` takes `--software` and `--server-jar`.

### Changed
- `plugin.yml` declares `api-version: '1.13'` (it was `'1.20.6'`), the lowest value every 1.13+ server accepts.
- The jar is Java 8 bytecode, except the `paper` adapter, which only loads on Java 21 and later. The manifest no longer claims Java 21.
- The build is a Maven reactor (`core`, `brigadier`, `paper`, `spigot`, `legacy`, `plugin`); the jar is still written to `target/VanillaPermissionsPlugin-<version>.jar`. Development builds are `1.1.0-SNAPSHOT`.
- On 1.8.8 - 1.12.2, `/tp` follows `minecraft.command.teleport` when `minecraft.command.tp` is unset, so `minecraft.command.* false` setups keep working. An explicit `minecraft.command.tp true` still wins.

### Fixed
- Console and command blocks no longer fail on `/scoreboard players … *` and on `/execute unless entity …` with an empty selection. Selector validation now skips non-player sources, and player checks keep vanilla's wildcard score holders (`*`) and empty selections. This also fixes the behaviour shipped on Paper in 1.0.0.
- Paper 1.20.5 no longer refuses the plugin because of its `api-version`.
- The operator-block nodes now also cover the 1.21.5+ test blocks and test instance blocks.
- LuckPerms refreshes no longer iterate the live online-player list from LuckPerms' event thread, which could lose a refresh when a player joined or left.
- Disabling the plugin while the server keeps running re-syncs commands to remove its checks (Spigot and Paper before 1.20.6), also when the shutdown probe is unavailable. The adapter is disabled before the permissions provider is closed, so a provider that fails to close cannot leave the checks installed.
- Legacy servers (1.8.8 - 1.12.2): a leading `/` on the command inside `/execute` no longer skips the checks. With `command-block-overrides` in `commands.yml`, the command inside `/execute` is checked as the command that actually runs. `/execute` with a position other than `~ ~ ~` is refused when the command it runs (after `commands.yml` alias expansion) uses a selector. `minecraft.selector=false` also covers `scoreboard players test`, and `*` counts every tracked score holder for selector limits. Selectors resolved off the main thread are refused, and a failed resolve is logged once as a warning.

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

# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

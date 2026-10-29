# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2026-10-29

First public release. VanillaPermissionsPlugin brings [DrexHD's VanillaPermissions](https://github.com/DrexHD/VanillaPermissions) for Fabric to Bukkit-based servers: permission nodes for every vanilla command, sub-command and `@` selector, set through LuckPerms or any other permissions plugin. One jar runs on Paper, Folia, Spigot, Purpur and Pufferfish from Minecraft 1.8.8 to 26.3. There is no config file.

New to the plugin? Start with [How permissions work](docs/how-it-works.md) and the [recipes](docs/recipes.md).

### Added
- A node for every vanilla command and each of its parts, for example `minecraft.command.time.set`, plus one node per game mode and one for changing another player's mode. `/minecraft:<command>` uses the same nodes.
- Selector control: `minecraft.selector` to allow `@` selectors, nodes to allow or deny targeting yourself, other players or other entities, a cap on the number of targets, and weights so players cannot target anyone ranked above them.
- Bypass nodes to join while not whitelisted, join a full server, keep your game mode when the server forces one, and skip the "moved too quickly" check. Operators do not get them automatically. The last two need Paper.
- Deny nodes for command blocks, structure blocks, jigsaws and test blocks, and for the debug stick on chosen blocks.
- The `/execute` additions of the original mod: `if|unless permission`, `feedback` and `oplevel`.
- On 1.8.8 - 1.12.2, sub-command nodes for ten commands: `gamemode`, `time`, `weather`, `kill`, `effect`, `give`, `clear`, `tp`/`teleport`, `execute` and `scoreboard`. Other commands keep their single node. See [Compatibility](docs/compatibility.md#188---1122).
- Works with any Bukkit permissions plugin. LuckPerms is recommended and gives contexts, wildcards and selector limits. With other plugins, Vault adds selector limits and makes the login bypasses work. Without a permissions plugin the plugin still runs and logs a warning.
- Tab completion only shows what a player may use, and updates as soon as you change a node in LuckPerms.
- `/vanillapermissions check <player> <node>` shows whether a player has a node and what an unset node means. `/vanillapermissions debug` shows your server version, whether it was tested, your permissions plugin and how many commands have checks.
- If the plugin cannot add its checks on a server, it says why in the startup log, leaves vanilla commands as they were and turns itself off.
- Releases come with a SHA-256 checksum and a signed GitHub attestation. See [Verifying a download](SECURITY.md#verifying-a-download).

### Security
- Denied commands are hidden from the command list and tab completion, and refused when typed.
- `/execute feedback entity` and `/execute oplevel` never give a player more permissions than they have.
- When a check fails with an error, the command or target is refused instead of allowed.

[Unreleased]: https://github.com/Soul4723/VanillaPermissionsPlugin/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Soul4723/VanillaPermissionsPlugin/releases/tag/v1.0.0

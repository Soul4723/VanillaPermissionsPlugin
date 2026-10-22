# VanillaPermissionsPlugin

[![Build](https://github.com/Soul4723/VanillaPermissionsPlugin/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/Soul4723/VanillaPermissionsPlugin/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/Soul4723/VanillaPermissionsPlugin)](https://github.com/Soul4723/VanillaPermissionsPlugin/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Granular permissions for vanilla Minecraft commands on Paper, Folia, Spigot and their forks, from 1.8.8 to 26.3. A drop-in Bukkit port of [DrexHD's VanillaPermissions](https://github.com/DrexHD/VanillaPermissions): your permissions plugin decides, node by node, who may use each part of a command such as `/gamemode creative`, `/time set` or `/tp <player>`, and whom they may target.

## Features

- **Per-command and per-argument nodes:** `minecraft.command.time`, `minecraft.command.time.set`, `minecraft.command.gamemode.creative`.
- **Selector control:** allow `@` selectors, limit who can be targeted (yourself, other players, entities), cap the number of targets, and rank players by weight.
- **Bypass nodes:** whitelist, player limit, forced game mode and movement checks.
- **Operator blocks and the debug stick:** take away command blocks, structure blocks, jigsaws and per-block debug stick use.
- **`/execute` extensions:** `if|unless permission`, `feedback` and `oplevel`.
- **Works with LuckPerms or any Bukkit permissions plugin.** No CommandAPI, Java agent, config file or startup flag. The server still parses and runs the commands; the plugin only adds permission checks.

## Supported servers

One jar covers all of these.

| Software | Minecraft | Java (what the server needs) |
| --- | --- | --- |
| [Paper](https://papermc.io/) | 1.8.8 - 26.3 | 8 up to 1.16.5, 17 for 1.17 - 1.20.4, 21 for 1.20.5 - 1.21.11, 25 for 26.x |
| [Folia](https://papermc.io/software/folia) | 1.20.6 - 26.3 | 21 / 25 |
| [Spigot](https://www.spigotmc.org/) | 1.8.8 - 26.3 | as for Paper |
| [Purpur](https://purpurmc.org/) | 1.20.6 - 26.3 | 21 / 25 |
| Pufferfish | 1.21 - 1.21.10 | 21 |

Not supported: Folia before 1.20.6 and Minecraft before 1.8.8. CI boots a spread of versions in every row; untested versions in between usually work (`/vanillapermissions debug` says whether yours was tested). On 1.8.8 - 1.12.2, which has no command tree, sub-command nodes exist for ten commands only; Spigot lacks the force-gamemode and move-speed bypasses. Details and limits: [Compatibility](docs/compatibility.md).

LuckPerms is recommended (5.5 needs Java 11+; use 5.4.x on Java 8). Without a permissions plugin the plugin still runs, but nobody can be given a node.

## Installation

1. Put `VanillaPermissionsPlugin-<version>.jar` from the [latest release](https://github.com/Soul4723/VanillaPermissionsPlugin/releases/latest) in `plugins/`. Each release has a `.sha256` file for `sha256sum -c`.
2. Restart the server. The log should show `Using Paper mode on Paper 1.21.11 (...)` (the mode and server vary) and `Added permission checks to <n> vanilla command nodes`. A `SEVERE` line means the plugin disabled itself and vanilla commands work as without it; see [Troubleshooting](docs/troubleshooting.md#reading-the-startup-log).
3. Run `/vanillapermissions debug` to see the mode, server version, permissions plugin and number of checked nodes.

There is no config file; everything is set through your permissions plugin.

## Quick start

Let a `member` group use `/time`:

```text
/lp group member permission set minecraft.command.time true
```

Operator commands stay closed to players until you grant them. Granting a command also opens its sub-commands (`/time set`, `/time add`) unless you close them:

```text
/lp group member permission set minecraft.command.time.set false
```

Check the result with `/vanillapermissions check <player> minecraft.command.time.set`. Selector limits, bypasses and more setups are in [Recipes](docs/recipes.md).

## Documentation

| | |
| --- | --- |
| [Permissions](docs/permissions.md) | How a node resolves, every node, wildcards, operator defaults |
| [Recipes](docs/recipes.md) | Copy-paste setups |
| [Permissions plugins](docs/providers.md) | LuckPerms, other plugins, Vault, running without one |
| [Compatibility](docs/compatibility.md) | Supported versions, features per version, limits |
| [Commands](docs/commands.md) | `/vanillapermissions` and the plugin's own nodes |
| [Troubleshooting](docs/troubleshooting.md) | FAQ and startup log |

The full index is [docs/README.md](docs/README.md). Ask questions and report bugs on [GitHub Issues](https://github.com/Soul4723/VanillaPermissionsPlugin/issues/new/choose); the templates say what to include.

## Building from source

`mvn verify` with JDK 21 and Maven writes the jar to `target/`. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Credits and license

The permission model, node names and `/execute` extensions come from [DrexHD/VanillaPermissions](https://github.com/DrexHD/VanillaPermissions), a Fabric mod; this is an independent Bukkit implementation of the same idea, and its differences from upstream are listed in [Compatibility](docs/compatibility.md#limits).

MIT. See [LICENSE](LICENSE).

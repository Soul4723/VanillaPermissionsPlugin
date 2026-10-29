# VanillaPermissionsPlugin

[![Build](https://github.com/Soul4723/VanillaPermissionsPlugin/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/Soul4723/VanillaPermissionsPlugin/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/Soul4723/VanillaPermissionsPlugin)](https://github.com/Soul4723/VanillaPermissionsPlugin/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Adds permission nodes to vanilla Minecraft commands, `@` selectors and operator features, on Paper, Folia, Spigot, Purpur and Pufferfish from 1.8.8 to 26.3. Without it, a vanilla command is all or nothing: you need the right op level. With it, your permissions plugin can allow or deny each command, sub-command and argument, for example `/gamemode creative` but not `/gamemode spectator`.

Each check is a permission *node*, a string such as `minecraft.command.time` that you set to true or false in your permissions plugin. Nodes you leave unset follow vanilla, so operators keep their commands and other players keep what they had. Only vanilla commands are affected; commands from other plugins keep their own permissions.

It brings [DrexHD's VanillaPermissions](https://github.com/DrexHD/VanillaPermissions), a Fabric mod, to Bukkit-based servers.

## Features

- **Per-command and per-argument nodes:** `minecraft.command.time`, `minecraft.command.time.set`, `minecraft.command.gamemode.creative`.
- **Selector control:** allow `@` selectors, limit who can be targeted (yourself, other players, entities), cap the number of targets, and give players a weight so they cannot target anyone ranked above them.
- **Bypass nodes:** let chosen players skip the whitelist, the player limit, the forced game mode or the "moved too quickly" check.
- **Operator blocks and the debug stick:** take command blocks, structure blocks, jigsaws and the debug stick (per block) away from operators.
- **`/execute` extensions:** `if|unless permission`, `feedback` and `oplevel`.
- **No config file:** everything is set through LuckPerms or any other Bukkit permissions plugin.

## Supported servers

One jar covers all of these. It runs on whatever Java your server version already needs.

| Software | Minecraft |
| --- | --- |
| [Paper](https://papermc.io/) | 1.8.8 - 26.3 |
| [Folia](https://papermc.io/software/folia) | 1.20.6 - 26.3 |
| [Spigot](https://www.spigotmc.org/) | 1.8.8 - 26.3 |
| [Purpur](https://purpurmc.org/) | 1.20.6 - 26.3 |
| Pufferfish | 1.21 - 1.21.10 |

Not supported: Folia before 1.20.6 and Minecraft before 1.8.8. Versions between the tested ones usually work, and `/vanillapermissions debug` tells you whether yours was tested.

Limits you may notice: on 1.8.8 - 1.12.2, sub-command nodes exist for ten commands only. The forced-game-mode and movement bypasses need Paper, so they do nothing on Spigot 1.13 and later. The rest is in [Compatibility](docs/compatibility.md).

LuckPerms is recommended (5.5 needs Java 11 or later; use 5.4.x on Java 8). Without a permissions plugin the plugin still runs, but only Bukkit defaults and operator status apply.

## Installation

1. Download `VanillaPermissionsPlugin-<version>.jar` and `VanillaPermissionsPlugin-<version>.jar.sha256` from the [latest release](https://github.com/Soul4723/VanillaPermissionsPlugin/releases/latest), for example `VanillaPermissionsPlugin-1.0.0.jar`. This is the only official download.
2. Optional: check the download. On Linux or macOS, in the folder holding both files, `sha256sum -c VanillaPermissionsPlugin-<version>.jar.sha256` should print `OK`. On Windows, run `certutil -hashfile VanillaPermissionsPlugin-<version>.jar SHA256` in Command Prompt and compare the result with the text in the `.sha256` file. [SECURITY.md](SECURITY.md#verifying-a-download) also shows how to check GitHub's signed release attestation.
3. Put the jar in `plugins/` and restart the server. The log should show a line such as `Using Paper mode on Paper 1.21.11 (...)` (the mode and server vary) and `Added permission checks to <n> vanilla command nodes`. If a line starts with `SEVERE`, the plugin switched itself off and the server runs as if it were not installed; see [Troubleshooting](docs/troubleshooting.md#reading-the-startup-log).
4. Run `/vanillapermissions debug` to see the mode, server version, whether that version was tested, your permissions plugin and the number of checked nodes.

To uninstall, remove the jar and restart.

## Quick start

Let the `member` group (use your own group name) use `/time`, from the console or as an operator:

```text
/lp group member permission set minecraft.command.time true
```

Operator commands stay closed to players until you grant them. Granting a command also opens its sub-commands (`/time set`, `/time add`) unless you close them:

```text
/lp group member permission set minecraft.command.time.set false
```

Members can now use `/time` but not `/time set`. Check the result with `/vanillapermissions check <player> minecraft.command.time.set`. Selector limits, bypasses and more setups are in [Recipes](docs/recipes.md).

## Documentation

| | |
| --- | --- |
| [Permissions](docs/permissions.md) | How a node resolves, every node, wildcards, operator defaults |
| [Recipes](docs/recipes.md) | Copy-paste setups |
| [Permissions plugins](docs/providers.md) | LuckPerms, other plugins, Vault, running without one |
| [Compatibility](docs/compatibility.md) | Supported versions, features per version, limits |
| [Commands](docs/commands.md) | `/vanillapermissions` and the plugin's own nodes |
| [Troubleshooting](docs/troubleshooting.md) | FAQ and startup log |

The full index is [docs/README.md](docs/README.md).

## Getting help and reporting bugs

Ask questions and report bugs on [GitHub Issues](https://github.com/Soul4723/VanillaPermissionsPlugin/issues/new/choose); the templates say what to include. Report security problems privately, not as a public issue: see [SECURITY.md](SECURITY.md).

## Building from source

`mvn verify` with JDK 21 and Maven writes the jar to `target/`. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Credits and license

The permission model, node names and `/execute` extensions come from [DrexHD/VanillaPermissions](https://github.com/DrexHD/VanillaPermissions); this is an independent Bukkit implementation of the same idea, and its differences from upstream are listed in [Compatibility](docs/compatibility.md#limits).

MIT. See [LICENSE](LICENSE).

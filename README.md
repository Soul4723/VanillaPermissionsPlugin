# VanillaPermissionsPlugin

[![Build](https://github.com/Soul4723/VanillaPermissionsPlugin/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/Soul4723/VanillaPermissionsPlugin/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/Soul4723/VanillaPermissionsPlugin)](https://github.com/Soul4723/VanillaPermissionsPlugin/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Granular permissions for vanilla Minecraft commands on Paper, Folia, Spigot and their forks, from 1.8.8 to 26.3: a drop-in port of [DrexHD's VanillaPermissions](https://github.com/DrexHD/VanillaPermissions).

Your permissions plugin decides, node by node, who may use each part of a vanilla command (`/gamemode creative`, `/time set`, `/tp <player>`…), whom they may target with names and `@` selectors, and a few vanilla operator features. The server still parses and runs the commands itself; the plugin only adds checks to the existing command tree (on 1.8.8 - 1.12.2, which has no command tree, it checks typed commands before they run, for ten commands).

- **Per-command and per-argument nodes:** `minecraft.command.time`, `minecraft.command.time.set`, `minecraft.command.gamemode.creative`…
- **Selector control:** allow `@` syntax, limit who can be targeted (self, other players, entities), cap the number of targets.
- **Bypass nodes:** whitelist, player limit, forced game mode and movement checks.
- **Operator blocks and the debug stick:** take away command blocks, structure blocks, jigsaws and per-block debug stick use.
- **Works with LuckPerms or any Bukkit permissions plugin.** No CommandAPI, Java agent or startup flag.

## Compatibility

| | Supported |
| --- | --- |
| Server software | [Paper](https://papermc.io/), [Folia](https://papermc.io/software/folia) (1.20.6 – 26.3), [Spigot](https://www.spigotmc.org/), [Purpur](https://purpurmc.org/) (1.20.6 – 26.3) and Pufferfish (1.21 – 1.21.10) |
| Minecraft | **1.8.8 through 26.3**, one jar, except Spigot 1.21.11 and 26.x (Paper never published 1.21.2, 26.1 or 26.1.0) |
| Java | 8 for 1.8.8 – 1.16.5, 17 for 1.17 – 1.20.4, 21 for 1.20.5 – 1.21.11, 25 for 26.x (whatever your server needs) |
| Permissions plugin | Optional. LuckPerms recommended (5.5 needs Java 11+; on Java 8 use 5.4.x); any Bukkit permissions plugin works |
| Not supported | Spigot 1.21.11 and 26.x; Folia before 1.20.6 |

Every listed version boots and installs cleanly in CI, and the main behaviour checks ran on live servers. What was verified, and how, is in [docs/compatibility.md](docs/compatibility.md#how-each-level-was-verified).

### Differences by platform

Not every feature exists on every version. The full list is in [docs/compatibility.md](docs/compatibility.md#limits).

- **Paper, Folia, Purpur and Pufferfish, 1.20.6 and later:** everything.
- **Spigot 1.13 and later, and Paper 1.13 – 1.20.5:** everything, except that the force-gamemode and move-speed bypasses need Paper's API and do nothing on Spigot (and on the older Paper versions listed in the limits).
- **1.8.8 – 1.12.2:** sub-command nodes for ten commands only; every other command keeps `minecraft.command.<name>`. No `/execute` additions and no debug stick nodes. 1.8.8 has no tab completion filtering.
- **Not supported:** Spigot 1.21.11 and 26.x (use Paper), and Folia before 1.20.6.

## Installation

Before you install:

- It needs Paper, Folia, Spigot or a fork of them, on a version listed above. Spigot 1.21.11 and 26.x are not supported: use Paper there.
- It does nothing useful without a permissions plugin such as LuckPerms: it adds the checks, your permissions plugin decides who passes them.
- There is no config file; everything is set through your permissions plugin.


1. Put `VanillaPermissionsPlugin-<version>.jar` from the [latest release](https://github.com/Soul4723/VanillaPermissionsPlugin/releases/latest) in `plugins/`. Each release also has a `.sha256` file to check the download with `sha256sum -c`.
2. Restart the server. A good start shows lines like these (the node count varies by version):

   ```text
   Using Paper mode on Paper 1.21.11 (checks on every vanilla command and sub-command)
   Using LuckPerms <version> for permission checks, ...
   Installed vanilla /execute permission, feedback, and oplevel extensions
   Added permission checks to <n> vanilla command nodes
   ```

   A `SEVERE` line starting with `Could not add permission checks` or ending with `is not supported` means the plugin disabled itself and vanilla commands work as they would without it. [Troubleshooting](docs/troubleshooting.md#reading-the-startup-log) shows what each line means, including the differences on 1.8.8 – 1.12.2.
3. Run `/vanillapermissions debug` to see the mode, the server version, the permissions plugin in use and the number of checked nodes.

## Quick start

Let a `member` group use `/time`:

```text
/lp group member permission set minecraft.command.time true
```

Operator commands such as `/time` stay closed to players until you grant them, so this is all a normal player group needs. It also opens `/time set`, `/time add` and `/time query`, because a sub-command with no node set follows its root. To take one away, set its node to `false`:

```text
/lp group member permission set minecraft.command.time.set false
```

Check the result with `/vanillapermissions check <player> minecraft.command.time.set`. [Read this first](docs/permissions.md) explains how a node resolves and how to find the node for a command. To take vanilla commands away from operators instead, see [Recipes](docs/recipes.md#take-every-vanilla-command-away-from-staff). More copy-paste setups are in [Recipes](docs/recipes.md).

## Documentation

| | |
| --- | --- |
| [Permissions](docs/permissions.md) | How a node resolves, every node, wildcards, operator defaults |
| [Recipes](docs/recipes.md) | Copy-paste setups |
| [Permissions plugins](docs/providers.md) | LuckPerms, other plugins, Vault, running without one |
| [Compatibility](docs/compatibility.md) | Supported versions, how they were verified, what is not supported |
| [Commands](docs/commands.md) | `/vanillapermissions` and the plugin's own nodes |
| [Troubleshooting and limitations](docs/troubleshooting.md) | FAQ and limitations |

The full index is [docs/README.md](docs/README.md).

## Support

Ask questions and report bugs on [GitHub Issues](https://github.com/Soul4723/VanillaPermissionsPlugin/issues/new/choose); the issue templates say what to include.

## Building from source

```text
mvn verify
```

Needs JDK 21 and Maven (JDK 21 builds every module, including the Java 8 ones). The jar is written to `target/`. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Credits

The permission model, node names and `/execute` extensions come from [DrexHD/VanillaPermissions](https://github.com/DrexHD/VanillaPermissions), a Fabric/Quilt mod. This plugin is an independent Bukkit-platform implementation of the same idea. Node differences from upstream are listed in [docs/compatibility.md](docs/compatibility.md#limits).

## License

MIT. See [LICENSE](LICENSE).

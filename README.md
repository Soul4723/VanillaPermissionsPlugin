# VanillaPermissionsPlugin

[![Build](https://github.com/Soul4723/VanillaPermissionsPlugin/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/Soul4723/VanillaPermissionsPlugin/actions/workflows/build.yml)
[![Compatibility](https://github.com/Soul4723/VanillaPermissionsPlugin/actions/workflows/compat.yml/badge.svg?branch=main)](https://github.com/Soul4723/VanillaPermissionsPlugin/actions/workflows/compat.yml)
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
| Java | 8 for 1.8.8 – 1.16.5, 17 for 1.17 – 1.20.4, 21 for 1.20.5 – 1.21.11, 25 for 26.x (whatever your server needs; the jar is Java 8 bytecode except the `paper` adapter, which is Java 21) |
| Permissions plugin | Optional. LuckPerms recommended (5.5 needs Java 11+; on Java 8 use 5.4.x); any Bukkit permissions plugin works |
| Not supported | Spigot 1.21.11 and 26.x; Folia before 1.20.6 |

Every listed Paper release, eight Spigot versions (1.8.8 – 1.20.4), four Folia builds from 1.20.6 through 26.3 and the Purpur and Pufferfish versions in the table boot and install cleanly in CI (1.21.5, 1.21.9 and 26.1.1 only have experimental Paper builds, so they are boot-tested only). Full behaviour checks ran on Paper 1.20.6 and 1.21.11, Spigot 1.20.4 and Paper 1.16.5, and Spigot 1.8.8 and 1.12.2; command, selector and LuckPerms-refresh checks ran on Folia 1.21.11. Spigot 1.20.5 – 1.21.10 is untested. Not every feature exists on every version; the features and limits are in [docs/compatibility.md](docs/compatibility.md).

## Installation

Before you install:

- It needs Paper, Folia, Spigot or a fork of them, on a version listed above. Spigot 1.21.11 and 26.x are not supported: use Paper there.
- It does nothing useful without a permissions plugin such as LuckPerms: it adds the checks, your permissions plugin decides who passes them.
- There is no config file; everything is set through your permissions plugin.


1. Put `VanillaPermissionsPlugin-<version>.jar` from the [latest release](https://github.com/Soul4723/VanillaPermissionsPlugin/releases/latest) in `plugins/`. Each release also has a `.sha256` file to check the download with `sha256sum -c`.
2. Restart the server. The log should show:
   - `Installed vanilla /execute permission, feedback, and oplevel extensions` (1.13 and later only)
   - `Added permission checks to <n> vanilla command nodes`
3. Run `/vanillapermissions debug` to see the server version, the permissions plugin in use and the number of checked nodes.

## Quick start

Let a `member` group use `/time`, and nothing else from vanilla (plugin commands are not affected):

```text
/lp group member permission set minecraft.command.* false
/lp group member permission set minecraft.command.time true
/lp group member permission set minecraft.command.time.* true
```

`minecraft.command.* false` also turns off `/me`, `/msg`, `/tell`, `/w`, `/teammsg`, `/tm`, `/trigger`, `/minecraft:help` and, through Paper's `minecraft.command.selector`, `@` selectors. To keep them, run these lines (the last one keeps `@` selectors; also see [Recipes](docs/recipes.md#what-minecraftcommand-false-also-turns-off)):

<details>
<summary>Lines to keep the everyday commands and <code>@</code> selectors</summary>

```text
/lp group member permission set minecraft.command.me true
/lp group member permission set minecraft.command.me.* true
/lp group member permission set minecraft.command.msg true
/lp group member permission set minecraft.command.msg.* true
/lp group member permission set minecraft.command.tell true
/lp group member permission set minecraft.command.w true
/lp group member permission set minecraft.command.teammsg true
/lp group member permission set minecraft.command.teammsg.* true
/lp group member permission set minecraft.command.tm true
/lp group member permission set minecraft.command.trigger true
/lp group member permission set minecraft.command.trigger.* true
/lp group member permission set minecraft.command.help true
/lp group member permission set minecraft.selector true
```

</details>

Granting a root also opens every sub-command you have not set: [Read this first](docs/permissions.md) explains that and how to find a node with `/lp verbose`.

Check the result with `/vanillapermissions check <player> minecraft.command.time.set`. More copy-paste setups are in [Recipes](docs/recipes.md).

## Documentation

| | |
| --- | --- |
| [Permissions](docs/permissions.md) | How a node resolves, every node, wildcards, operator defaults |
| [Recipes](docs/recipes.md) | Copy-paste setups |
| [Permissions plugins](docs/providers.md) | LuckPerms, other plugins, Vault, running without one |
| [Compatibility](docs/compatibility.md) | Supported versions, how they were verified, what is not supported |
| [Commands](docs/commands.md) | `/vanillapermissions` and the plugin's own nodes |
| [Troubleshooting and limitations](docs/troubleshooting.md) | FAQ and limitations |
| [Roadmap](docs/roadmap.md) | Other versions and platforms |

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

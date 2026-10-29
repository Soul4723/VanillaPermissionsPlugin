# VanillaPermissionsPlugin

[![Build](https://github.com/Soul4723/VanillaPermissionsPlugin/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/Soul4723/VanillaPermissionsPlugin/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/Soul4723/VanillaPermissionsPlugin)](https://github.com/Soul4723/VanillaPermissionsPlugin/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Permission nodes for every vanilla Minecraft command, sub-command and `@` selector, set through LuckPerms or any other permissions plugin.

Without this plugin, a vanilla command such as `/gamemode` is all or nothing: a player either has it, with every mode and every target, or does not. With it, you decide per part. Let staff use `/gamemode spectator` on themselves but not creative. Let helpers `/tp` to players but not to coordinates. Let VIPs join a full server. Take command blocks away from your operators.

It is a port of [DrexHD's VanillaPermissions](https://github.com/DrexHD/VanillaPermissions) for Fabric, and uses the same node names.

## Supported servers

| Software | Minecraft |
| --- | --- |
| [Paper](https://papermc.io/) | 1.8.8 - 26.3 |
| [Folia](https://papermc.io/software/folia) | 1.20.6 - 26.3 |
| [Spigot](https://www.spigotmc.org/) | 1.8.8 - 26.3 |
| [Purpur](https://purpurmc.org/) | 1.20.6 - 26.3 |
| Pufferfish | 1.21 - 1.21.10 |

One jar covers all of them. [LuckPerms](https://luckperms.net/) is recommended: use 5.5 or later, or 5.4.x on a Java 8 server. A few features depend on your server version or software; see [Compatibility](docs/compatibility.md).

## Installation

1. Download `VanillaPermissionsPlugin-<version>.jar` from the [latest release](https://github.com/Soul4723/VanillaPermissionsPlugin/releases/latest). This is the only official download. To check the file, see [Verifying a download](SECURITY.md#verifying-a-download).
2. Put the jar in your server's `plugins` folder and restart the server.
3. Run `/vanillapermissions debug` in the console. It shows your server version, your permissions plugin and how many vanilla commands now have permission checks.

There is no config file. Everything is set in your permissions plugin. To uninstall, remove the jar and restart.

If the startup log has a `SEVERE` line from the plugin, it has switched itself off and vanilla commands work as before. See [Reading the startup log](docs/troubleshooting.md#reading-the-startup-log).

## Quick start

Let your staff switch themselves between survival and spectator, for example to watch players with a vanish plugin. Run these in the console, or in game as an operator. They use a LuckPerms group called `staff` and a player called `Steve`; use your own names. Create the group with `/lp creategroup staff` if you do not have one yet.

```text
/lp group staff permission set minecraft.command.gamemode true
/lp group staff permission set minecraft.command.gamemode.* false
/lp group staff permission set minecraft.command.gamemode.survival true
/lp group staff permission set minecraft.command.gamemode.spectator true
/lp user Steve parent add staff
```

Steve can now use `/gamemode spectator` and `/gamemode survival` on themselves. They cannot use creative or adventure mode, and cannot change anyone else's game mode. Changes apply at once, and Steve's tab completion only shows what they may use.

The first line grants `/gamemode`. The second closes every mode and target below it. The last two open the two modes you want. [How permissions work](docs/how-it-works.md) explains this in five minutes.

## Documentation

| Page | Read it to |
| --- | --- |
| [How permissions work](docs/how-it-works.md) | Understand nodes, true, false and unset, and operators. Start here. |
| [Recipes](docs/recipes.md) | Copy setups for staff, builders, moderators, VIPs, event hosts and operators. |
| [Permission nodes](docs/permissions.md) | Look up any node: commands, selectors, bypasses, command blocks, the debug stick. |
| [Permissions plugins](docs/providers.md) | Use LuckPerms, GroupManager or another plugin, with or without Vault. |
| [Compatibility](docs/compatibility.md) | Check what works on your server version, and the known limits. |
| [The /vanillapermissions command](docs/commands.md) | Check a player's node, or see what the plugin found on your server. |
| [Troubleshooting](docs/troubleshooting.md) | Fix a node that does not work, and read the startup log. |

## Getting help

Ask questions and report bugs on [GitHub Issues](https://github.com/Soul4723/VanillaPermissionsPlugin/issues/new/choose). Please include the output of `/vanillapermissions debug`. Report security problems privately, as described in [SECURITY.md](SECURITY.md).

## Building from source

Run `mvn verify` with JDK 21 and Maven. The jar is written to `target/`. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Credits and license

The permission model, node names and `/execute` additions come from [DrexHD/VanillaPermissions](https://github.com/DrexHD/VanillaPermissions). This plugin is an independent implementation of the same idea for Bukkit-based servers. What it cannot offer from the original is listed in [Compatibility](docs/compatibility.md#limits).

Released under the MIT license. See [LICENSE](LICENSE).

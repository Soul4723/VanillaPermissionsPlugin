# Compatibility

One jar, `VanillaPermissionsPlugin-<version>.jar`, runs on every server listed here. It needs no setting: the plugin looks at your server when it starts.

## Supported servers

| Software | Minecraft | Java |
| --- | --- | --- |
| Paper | 1.8.8 - 26.3 | 8 for 1.8.8 - 1.16.5, 17 for 1.17 - 1.20.4, 21 for 1.20.5 - 1.21.11, 25 for 26.x |
| Folia | 1.20.6 - 26.3 | 21 for 1.20.6 - 1.21.11, 25 for 26.x |
| Spigot | 1.8.8 - 26.3 | as Paper |
| Purpur | 1.20.6 - 26.3 | as Paper |
| Pufferfish | 1.21 - 1.21.10 | 21 |

The startup log names a mode: Paper mode for Paper, Folia and their forks from 1.20.6, Spigot mode for Spigot and for Paper 1.13 - 1.20.5, and legacy mode for 1.8.8 - 1.12.2. `/vanillapermissions debug` shows it too.

These versions are tested:

- **Paper:** 1.8.8, 1.12.2, 1.13.2, 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.3 - 1.21.11, 26.1.1, 26.1.2, 26.2 and 26.3.
- **Spigot:** 1.8.8, 1.12.2, 1.13.2, 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.4, 1.20.6, 1.21.10, 1.21.11 and 26.3.
- **Folia:** 1.20.6, 1.21.11, 26.1.2 and 26.3.
- **Purpur:** 1.20.6, 1.21.11, 26.1.2, 26.2 and 26.3. Purpur has no 1.21.2 build.
- **Pufferfish:** 1.21.1 and 1.21.10. Pufferfish publishes builds for 1.21, 1.21.1, 1.21.3, 1.21.7, 1.21.8 and 1.21.10 only.

Paper has no builds for 1.21.2, 26.1 or 26.1.0. Paper published only experimental builds for 1.20.5, 1.21.5, 1.21.9 and 26.1.1, and those are boot-tested only.

Versions between the tested ones usually work. `/vanillapermissions debug` tells you whether your version was tested.

Not supported: Folia before 1.20.6 and Minecraft before 1.8.8.

LuckPerms 5.5 needs Java 11 or later. On a Java 8 server use LuckPerms 5.4.x or another permissions plugin. See [Permissions plugins](providers.md).

## What works where

Command nodes and selector control work on every supported version. Some features depend on the Minecraft version or on your server software:

| Feature | Where it works |
| --- | --- |
| Command nodes, `minecraft.selector`, selector scope, limit and weight | All versions. On 1.8.8 - 1.12.2 for the ten commands with sub-nodes; selectors in other commands only through the server's `minecraft.command.selector`. |
| `/gamemode` per-mode nodes | All versions |
| `/execute if\|unless permission`, `feedback silent`, `oplevel` | 1.13+ |
| `/execute feedback console\|entity` | 1.17+ |
| Whitelist and player-limit bypasses | All versions |
| Force-gamemode bypass | Paper 1.16.5+ and forks; 1.8.8 - 1.12.2 |
| Move-speed bypass | Paper 1.20.4+ and forks; 1.8.8 - 1.12.2 |
| Operator blocks | All versions. On 1.8.8 - 1.12.2: command blocks, plus structure blocks from 1.10. |
| Debug stick per-block nodes | 1.13+ |

## 1.8.8 - 1.12.2

Before 1.13 the server checks one node per command, `minecraft.command.<name>`, and nothing finer. On these versions the plugin adds the modern sub-nodes for ten commands. Every other command keeps `minecraft.command.<name>` and nothing more. The rest works as in [How permissions work](how-it-works.md).

The sub-nodes use the modern names, so a configuration written for 1.13 and later works unchanged. All start with `minecraft.command.`:

| Command | Nodes |
| --- | --- |
| `gamemode` | `gamemode.{survival,creative,adventure,spectator}`, and `.target` for another player. The old spellings `s`, `c`, `a`, `sp`, `0` - `3` and `01` use the node of their mode. |
| `time` | `time.set.{day,night,time}`, `time.add.time`, `time.query.{daytime,gametime}`, and `time.query.day` (1.12) |
| `weather` | `weather.{clear,rain,thunder}`, and `.duration` |
| `kill` | `kill.targets` |
| `effect` | `effect.clear.targets`, `.effect`; `effect.give.targets.effect`, then `.seconds`, `.amplifier`, `.hideParticles` |
| `give` | `give.targets.item.count` |
| `clear` | `clear.targets.item.maxCount` |
| `tp` / `teleport` | `teleport.destination`, `teleport.targets.destination`, `teleport.location`, `teleport.targets.location`, `teleport.targets.location.rotation`. The root node stays `tp` or `teleport`, as typed. |
| `execute` | `execute.as.targets`, `execute.if.block.pos.block` (the `detect` form), `execute.run`. The command inside is checked for the player who ran it. |
| `scoreboard` | `scoreboard.objectives.…`, `scoreboard.players.…`; `scoreboard teams` uses `team.…` and `scoreboard players tag` uses `tag.…` |

Selector nodes (`minecraft.selector`, scope, limit and weight) apply to these ten commands. In any other command on these versions, only the server's own `minecraft.command.selector` node controls `@` selectors, and `minecraft.selector` grants it.

- **Roots that differ:** `/tell` is checked as `minecraft.command.tell`, and `/msg` and `/w` on 1.12 as well. It is also denied when `minecraft.command.msg` is `false`. `/xp` is checked as `minecraft.command.xp` and also denied when `minecraft.command.experience` is `false`. `/tp` is checked as `minecraft.command.tp` and also denied when `minecraft.command.teleport` is `false`, unless `minecraft.command.tp` is explicitly `true`. So a setup that denies `minecraft.command.*` keeps working. `/msg` and `/w` do not exist on 1.8.8.
- **Grant `/tp` with its own node:** `minecraft.command.teleport` does not open `/tp` here. Grant `minecraft.command.tp`.
- **Aliases:** `minecraft:<command>` and aliases from `commands.yml` use the same nodes.
- **Console and command blocks** are never checked.
- **A denied player** sees "You do not have permission to use this command". A malformed selector gets the plugin's own denial message.
- **Operator blocks** cover command blocks, and structure blocks from 1.10.
- **Only on 1.13 and later:** the `/execute` additions and the debug stick nodes.
- **Not run on 1.9.4 - 1.11.2:** the command rules were written against 1.12.2 and have not been run on these versions.

## Folia

Folia's own behaviour applies, with or without this plugin:

- A full Folia server refuses an extra player even when the player-limit bypass is granted.
- A `permissions.yml` node with `default: true` is not applied. `default: notop` works.
- Commands Folia removes, such as `/scoreboard`, `/data` and `/reload`, have no nodes to check. The exact set depends on the Folia version.
- Selectors for anything but players only see entities in the same region, as in Folia's own commands.

## Limits

### Features

- **Bypasses:** the force-gamemode and move-speed bypasses need Paper, so they do nothing on Spigot 1.13 and later. They also need Paper 1.16.5 or later (force-gamemode) and Paper 1.20.4 or later (move-speed); on older Paper the nodes have no effect. `/vanillapermissions check` says so.
- **Random selectors:** `@r` and `sort=random` are checked against every entity they could pick, because the server draws again when the command runs.
- **`/execute` forks:** on 1.13 and later, selectors after `as` and `at` are not checked for each fork. See [Selectors](permissions.md#selectors).
- **Score-holder selectors on Spigot and Paper 1.13 - 1.20.2:** the server passes `@a` in `/scoreboard players set @a obj 1` as plain names, so the plugin cannot tell a player from an entity. Only `minecraft.selector` and the limit apply there, not scope or weight. From 1.20.3 the usual rules apply.

### Nodes from the original Fabric mod that are not available

The server offers no way to add these:

- `minecraft.bypass.spawn-protection`
- `minecraft.bypass.chat-speed`
- `minecraft.bypass.move-speed.vehicle.<entity>`
- `minecraft.debug_chart`
- `minecraft.<query|load>.<entity|block>`
- `minecraft.adminbroadcast.receive`: use Paper's `minecraft.admin.command_feedback` instead.
- the `edit` action of operator blocks

### Runtime

- **Disabling the plugin while the server runs:** a plugin manager can do this on Spigot and on Paper before 1.20.6. The plugin then removes its checks from the `minecraft:` copies of the commands. Enabling or disabling it while players are online can make a command-list update fail for them.
- **Console feedback:** with `/execute feedback console` the console may show a result twice.
- **Java 26** was not tried. On Spigot 1.13 and later and Paper 1.13 - 1.20.5 the plugin changes fields that Java marks as read-only. Java 26 warns about this by default ([JEP 500](https://openjdk.org/jeps/500)), and a later Java is expected to refuse it.

### On 1.8.8 - 1.12.2

- Only the ten commands above have sub-nodes.
- 1.8.8 does not hide denied tab completions. They are refused when the player runs them.
- Commands run by other plugins and commands run by clicking signs are not checked. Aliases from `commands.yml` are.
- Selectors inside `/execute` are resolved from each target's position. If an execution coordinate is not exactly `~` and the command inside uses a selector, the command is denied with the usual no-permission message.
- The move-speed bypass does not cover moves in a vehicle.
- Denying `view` on operator blocks is only partly enforced on 1.8.8 command blocks and 1.12.2 structure blocks. The client still lets the player edit the block.

## How versions are tested

- **Boot matrix:** [CI](../.github/workflows/build.yml) starts every server listed above with the plugin and LuckPerms, and checks that the plugin installed its checks. It runs on every push and pull request, and twice a week.
- **Live tests:** a bot player ran commands, selectors, bypasses, operator blocks and the debug stick on Paper 1.16.5, 1.20.6 and 1.21.11, Spigot 1.8.8, 1.12.2, 1.20.4, 1.21.11 and 26.3, and Folia 1.21.11 (commands, selectors, aliases and LuckPerms refresh only). Every [recipe](recipes.md) is tested on Paper 1.21.11 and Spigot 1.21.11. See [Testing](development/testing.md).
- **Untested versions:** for example 1.14.x, 1.15.x, 1.9.4 - 1.11.2, Spigot 1.18.x and 1.19.x other than 1.18.2 and 1.19.4, and Spigot 1.20.5, 1.21 - 1.21.9, 26.1.x and 26.2. The plugin logs a warning at startup on a version outside the tested list. If it cannot add its checks, it logs why, restores the vanilla commands and disables itself.

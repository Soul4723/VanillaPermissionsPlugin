# Compatibility

One jar, `VanillaPermissionsPlugin-<version>.jar`, covers **Minecraft 1.8.8 through 26.3** on Paper, Folia, Spigot, Purpur and Pufferfish. Folia is supported from 1.20.6. Paper has no builds for 1.21.2, 26.1 or 26.1.0, so those versions cannot be run on Paper at all.

The jar contains three adapters. The plugin picks one at startup by looking at the server (not at the version string), most specific first:

| Adapter | Serves | How it reaches vanilla commands |
| --- | --- | --- |
| `paper` | Paper and Folia 1.20.6 and later, and forks (Purpur, Pufferfish) | Paper's lifecycle `COMMANDS` event. Needs Java 21 (Java 25 for 26.x). |
| `spigot` | Spigot 1.13 through 26.3, Paper 1.13 through 1.20.5 | The Brigadier dispatcher inside CraftBukkit, patched in `onEnable`. |
| `legacy` | Spigot and Paper 1.8.8 through 1.12.2 | No Brigadier. Typed commands are checked before they run; see [1.8.8 - 1.12.2](permissions.md#188---1122-legacy-servers). |

The startup log and `/vanillapermissions debug` name the adapter in use as the mode (for example `Using Spigot mode on Paper 1.16.5`).

## Supported versions

Java is the version the server software needs. The jar itself is compiled for Java 8, except the `paper` adapter, which only loads on Java 21 and later. LuckPerms 5.5 needs Java 11 or later; on a Java 8 server use LuckPerms 5.4.x or another permissions plugin.

Every version below boots and installs cleanly in CI. See [How each level was verified](#how-each-level-was-verified).

### Paper

| Minecraft | Java | Adapter |
| --- | --- | --- |
| 1.8.8, 1.12.2 | 8 | legacy |
| 1.13.2, 1.16.5 | 8 | spigot |
| 1.17.1, 1.18.2, 1.19.4, 1.20.4 | 17 | spigot |
| 1.20.5 | 21 | spigot |
| 1.20.6, 1.21, 1.21.1, 1.21.3 - 1.21.11 | 21 | paper |
| 26.1.1, 26.1.2, 26.2, 26.3 | 25 | paper |

Paper 1.8.8 - 1.16.5 also run on newer Java (11 or later); 1.17.x accepts Java 16 or 17. Paper published only experimental builds for 1.20.5, 1.21.5, 1.21.9 and 26.1.1; those are boot-tested only.

### Spigot

| Minecraft | Java | Adapter |
| --- | --- | --- |
| 1.8.8, 1.12.2 | 8 | legacy |
| 1.13.2, 1.16.5 | 8 | spigot |
| 1.17.1, 1.18.2, 1.19.4, 1.20.4 | 17 | spigot |
| 1.20.6, 1.21.10, 1.21.11 | 21 | spigot |
| 26.3 | 25 | spigot |

### Folia

Folia 1.20.6, 1.21.11, 26.1.2 and 26.3 are tested. It uses the `paper` adapter. Folia's own behaviour applies, with or without this plugin:

- A full Folia server refuses an extra player even when the player-limit bypass is granted.
- A `permissions.yml` node with `default: true` is not applied; `default: notop` works.
- Commands Folia removes (for example `/scoreboard`, `/data` and `/reload`) have no permission nodes to check; the exact set depends on the Folia version.
- Non-player selectors only see region-local entities, as in Folia's vanilla command handling.

### Purpur and Pufferfish

Both are Paper forks and use the `paper` adapter, with the same Java requirements as Paper.

| Software | Supported | Tested in CI |
| --- | --- | --- |
| Purpur | 1.20.6 - 26.3 (Purpur has no 1.21.2 build) | 1.20.6, 1.21.11, 26.1.2, 26.2, 26.3 |
| Pufferfish | 1.21 - 1.21.10 (Pufferfish publishes builds for 1.21, 1.21.1, 1.21.3, 1.21.7, 1.21.8 and 1.21.10 only) | 1.21.1, 1.21.10 |

### Feature availability

Command nodes and selector control work on every supported version. Some features depend on the Minecraft version or on what the server software offers:

| Feature | Where it works |
| --- | --- |
| Command nodes, `minecraft.selector`, selector scope, limit and weight | All versions. On 1.8.8 - 1.12.2 for the ten commands with sub-nodes; selectors in other legacy commands only through CraftBukkit's `minecraft.command.selector`. |
| `/gamemode` per-mode nodes | All versions |
| `/execute if\|unless permission`, `feedback silent`, `oplevel` | 1.13+ |
| `/execute feedback console\|entity` | 1.17+ |
| Whitelist and player-limit bypasses | All versions |
| Force-gamemode bypass | Paper 1.16.5+ and forks; 1.8.8 - 1.12.2 |
| Move-speed bypass | Paper 1.20.4+ and forks; 1.8.8 - 1.12.2 |
| Operator blocks | All versions. On 1.8.8 - 1.12.2: command blocks, plus structure blocks from 1.10. |
| Debug stick per-block nodes | 1.13+ |

## How each level was verified

- **Boot and install:** the boot matrix in [`.github/workflows/build.yml`](../.github/workflows/build.yml) starts every server listed above with the plugin and LuckPerms and checks that the plugin installs its permission checks and that the number of checked nodes reaches a per-row minimum. It runs on every push and pull request, and twice a week. Spigot cannot be downloaded, so CI builds each jar once with BuildTools and caches it.
- **Live behaviour checks:** commands, selectors, bypasses, operator blocks and the debug stick were exercised on live servers with a bot player ([live tests](development/testing.md)): Paper 1.20.6 and 1.21.11 (`paper` adapter), Spigot 1.20.4, 1.21.11 and 26.3 and Paper 1.16.5 (`spigot` adapter), and Spigot 1.8.8 and 1.12.2 (`legacy` adapter). Folia 1.21.11 passed the command, selector, alias and LuckPerms-refresh checks.
- **Untested versions:** versions not listed above were not booted, for example 1.14.x, 1.15.x, 1.9.4 - 1.11.2, Spigot 1.18.x and 1.19.x other than 1.18.2 and 1.19.4, and Spigot 1.20.5, 1.21 - 1.21.9, 26.1.x and 26.2. They usually work: support is decided by feature detection when the plugin installs. The plugin logs a warning once at startup on a (software, version) pair outside the tested list, and `/vanillapermissions debug` marks your version as tested or untested. If the command tree cannot be patched, the plugin logs why, restores the vanilla commands and disables itself.

## Limits

### Not supported

| Software and versions | What happens |
| --- | --- |
| Folia before 1.20.6 | Not supported. The Paper lifecycle command API required by the adapter is unavailable. |
| Minecraft before 1.8.8 | Not supported. |

### Features

- **Bypasses:** the force-gamemode and move-speed bypasses need Paper's events, so they do nothing on Spigot 1.13+. The force-gamemode bypass also needs Paper 1.16.5 or later and the move-speed bypass Paper 1.20.4 or later; on older Paper versions the nodes have no effect. `/vanillapermissions check` says so.
- **Selectors:** random selectors (`@r`, `sort=random`) are checked against every entity they could pick, because the server draws again when the command runs. On 1.13+, selectors in `/execute` forks (`as`, `at`) are not checked per fork. See [Selectors](permissions.md#selectors).
- **Upstream nodes not added**, because the server has no API for them: `minecraft.bypass.spawn-protection`, `minecraft.bypass.chat-speed`, `minecraft.bypass.move-speed.vehicle.<entity>`, `minecraft.debug_chart`, `minecraft.<query|load>.<entity|block>`, `minecraft.adminbroadcast.receive` (use Paper's `minecraft.admin.command_feedback`) and the operator block `edit` action.

### Runtime

- **Disabling at runtime:** if a plugin manager disables this plugin while the server keeps running (Spigot and Paper before 1.20.6), the plugin re-syncs commands to remove its checks from the `minecraft:` copies. Enabling or disabling the plugin while players are online may race the server's asynchronous command-tree send and cause a command-list update to fail.
- **Console feedback:** with `/execute feedback console` the console may show a result twice (the direct output plus the admin log line).
- **Java 26** was not tried. On Spigot 1.13 and later and Paper 1.13 - 1.20.5 the plugin writes `private final` fields; JDK 26 warns about that by default ([JEP 500](https://openjdk.org/jeps/500)), and a later JDK is expected to refuse it.

### 1.8.8 - 1.12.2

- Only ten commands have sub-nodes ([list](permissions.md#188---1122-legacy-servers)); all others keep `minecraft.command.<name>`.
- 1.8.8 has no tab completion filtering; denied completions are refused when run.
- Commands run by other plugins (`performCommand`, `dispatchCommand`) and commands run by clicking signs are not checked. `commands.yml` aliases are.
- Selectors inside `/execute` are resolved from each target's position; if any execution coordinate is not exactly `~` and the inner command (after alias expansion) uses a selector in a selector-capable argument, the command is denied with the normal no-permission message.
- The move-speed bypass does not cover vehicle moves.
- The `view` deny of operator blocks is only partly enforced on 1.8.8 command blocks and 1.12.2 structure blocks: part of it is client-side, and edit packets still pass.

## Permissions plugins

See [Permissions plugins](providers.md). CI runs LuckPerms 5.5 on Java 11 and later and LuckPerms 5.4.158 on Java 8.

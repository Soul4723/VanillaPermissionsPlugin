# Compatibility

One jar, `VanillaPermissionsPlugin-<version>.jar`, covers **Minecraft 1.8.8 through 26.3** on Paper, Folia, Spigot, Purpur and Pufferfish, with one exception: Spigot 1.21.11 and 26.x are not supported (see [Limits](#limits)). Folia is supported from 1.20.6. Paper's download API (`fill.papermc.io`) has no builds for 1.21.2, 26.1 or 26.1.0, so those versions cannot be run on Paper at all (1.21.1 is followed by 1.21.3, and the 26.1 line starts at 26.1.1).

The jar contains three adapters. The plugin picks one at startup by looking at the server (not at the version string), most specific first:

| Adapter | Serves | How it reaches vanilla commands |
| --- | --- | --- |
| `paper` | Paper and Folia 1.20.6 and later, and forks (Purpur, Pufferfish) | Paper's lifecycle `COMMANDS` event. Needs Java 21 (Java 25 for 26.x). |
| `spigot` | Spigot 1.13 through 1.21.10 (1.20.5 and later untested), Paper 1.13 through 1.20.5 | The Brigadier dispatcher inside CraftBukkit, patched in `onEnable`. |
| `legacy` | Spigot and Paper 1.8.8 through 1.12.2 | No Brigadier. Typed commands are checked before they run; see [1.8.8 - 1.12.2](permissions.md#188---1122-legacy-servers). |

See [Architecture](development/architecture.md) for how they are chosen.

## Supported versions

Java is the version the server software needs. The jar itself is compiled for Java 8, except the `paper` adapter, which only loads on Java 21 and later. LuckPerms 5.5 needs Java 11 or later; on a Java 8 server use LuckPerms 5.4.x (CI uses 5.4.158) or another permissions plugin.

### Paper

| Minecraft | Java | Adapter | Verified |
| --- | --- | --- | --- |
| 1.8.8, 1.12.2 | 8 | legacy | Boot and install in CI |
| 1.13.2, 1.16.5 | 8 | spigot | Boot and install in CI. Live behaviour checks on 1.16.5. |
| 1.17.1 | 17 | spigot | Boot and install in CI |
| 1.18.2, 1.19.4, 1.20.4 | 17 | spigot | Boot and install in CI |
| 1.20.5 | 21 | spigot | Boot and install in CI. Paper only published experimental builds. |
| 1.20.6 | 21 | paper | Boot and install in CI; full live behaviour checks |
| 1.21, 1.21.1, 1.21.3, 1.21.4, 1.21.6, 1.21.7, 1.21.8, 1.21.10 | 21 | paper | Boot and install in CI |
| 1.21.11 | 21 | paper | Boot and install in CI; full live behaviour checks |
| 1.21.5, 1.21.9 | 21 | paper | Boot and install in CI. Paper only published experimental builds. |
| 26.1.1 | 25 | paper | Boot and install in CI. Paper only published an experimental build. |
| 26.1.2, 26.2 | 25 | paper | Boot and install in CI |
| 26.3 | 25 | paper | Boot and install in CI; live player checks ([how](development/testing.md#minecraft-26x)) |

Paper 1.8.8 - 1.16.5 also run on newer Java (11 or later) if you prefer; 1.17.x accepts Java 16 or 17.

### Spigot

| Minecraft | Java | Adapter | Verified |
| --- | --- | --- | --- |
| 1.8.8, 1.12.2 | 8 | legacy | Boot and install in CI; live behaviour checks |
| 1.13.2 | 8 | spigot | Boot and install in CI |
| 1.16.5 | 8 | spigot | Boot and install in CI |
| 1.17.1 | 17 | spigot | Boot and install in CI |
| 1.18.2, 1.19.4 | 17 | spigot | Boot and install in CI |
| 1.20.4 | 17 | spigot | Boot and install in CI; live behaviour checks |

Spigot cannot be downloaded, so CI builds each jar once with BuildTools and caches it.

### Folia

Four Folia builds (1.20.6, 1.21.11, 26.1.2 and 26.3) are pinned in the boot matrix in `.github/workflows/build.yml`, which checks that the plugin installs cleanly with LuckPerms.

Folia 1.21.11 passed the `gamemode`, `execute`, `selectors`, `aliases` and `folia` live scenarios with LuckPerms, including user and group changes refreshing both bots' command trees. No `UnsupportedOperationException` was logged.

Folia's own behaviour applies, with or without this plugin:

- A full Folia server refuses an extra player even when the player-limit bypass is granted ([connection-capacity check](https://github.com/PaperMC/Folia/blob/ver/1.21.11/folia-server/minecraft-patches/features/0001-Region-Threading-Base.patch)).
- A `permissions.yml` node with `default: true` is not applied; `default: notop` works ([permission-default calculation](https://github.com/PaperMC/Folia/blob/ver/1.21.11/folia-server/paper-patches/features/0001-Region-Threading-Base.patch)).
- Commands Folia removes (for example `/scoreboard`, `/data` and `/reload`) have no permission nodes to check; the exact set depends on the Folia version.
- Non-player selectors only see region-local entities, as in Folia's vanilla command handling.

Purpur and Pufferfish are Paper forks and use the `paper` adapter; their boot rows are described under [Purpur and Pufferfish builds](#purpur-and-pufferfish-builds).

### Feature availability

Command nodes and selector control work on every supported version. Some features depend on the Minecraft version or on what the server software offers:

| Feature | Where it works |
| --- | --- |
| Command nodes, `minecraft.selector`, selector scope, limit and weight | All versions. On 1.8.8 - 1.12.2 for the ten commands with sub-nodes; selectors in other legacy commands only through CraftBukkit's `minecraft.command.selector`. |
| `/gamemode` per-mode nodes | All versions |
| `/execute if\|unless permission`, `feedback silent`, `oplevel` | 1.13+ |
| `/execute feedback console\|entity` | 1.17+ |
| Whitelist and player-limit bypasses | All versions |
| Force-gamemode bypass | Paper 1.16.5+; 1.8.8 - 1.12.2 |
| Move-speed bypass | Paper 1.20.4+; 1.8.8 - 1.12.2 |
| Operator blocks | All versions. On 1.8.8 - 1.12.2: command blocks, plus structure blocks from 1.10. |
| Debug stick per-block nodes | 1.13+ |

The rows for Paper 1.16.5+ and 1.20.4+ also apply to the forks of the matching Paper version.

## How each level was verified

- **Boot and install:** the boot matrix in [`.github/workflows/build.yml`](../.github/workflows/build.yml) starts each listed server with the plugin and LuckPerms (5.5 on Java 11 and later, 5.4.158 on Java 8), and checks that the plugin installs its permission checks and that the number of checked nodes reaches a per-row minimum (`scripts/compat-smoke.sh`). It runs on every push and pull request, and twice a week on a schedule. Spigot rows use jars built with BuildTools and cached.
- **Live behaviour checks:** commands, selectors, bypasses, operator blocks and the debug stick were exercised on live servers with a bot player ([live tests](development/testing.md)). Run on Paper 1.20.6 and 1.21.11 (paper adapter, full), Spigot 1.20.4 and Paper 1.16.5 (spigot adapter), and Spigot 1.8.8 and 1.12.2 (legacy adapter). Folia 1.21.11 also passed command, selector, alias and LuckPerms-refresh checks. Results of individual live runs are in the descriptions of the pull requests that ran them, not in the repository.
- **Coverage:** every listed Paper release, eight Spigot versions (1.8.8 – 1.20.4), four Folia builds from 1.20.6 through 26.3 and the Purpur and Pufferfish versions below boot and install cleanly in CI. Paper's 1.21.5, 1.21.9 and 26.1.1 builds are experimental, so those versions are boot-tested only. Spigot 1.20.5 – 1.21.10 is untested.
- **Experimental builds:** Paper published only ALPHA builds for 1.20.5, 1.21.5, 1.21.9 and 26.1.1. They are boot-tested only, and Paper itself marks them experimental.

Versions not listed in the tables were not booted, for example 1.14.x, 1.15.x, 1.9.4 - 1.11.2, and Spigot 1.18 and 1.19.x other than 1.18.2 and 1.19.4.

The plugin logs a warning once at startup on a (software, version) pair outside the tested list, and `/vanillapermissions debug` marks your version as tested or untested, with the tested range for your server software. Support is decided by feature detection when the plugin installs, so an untested version may work; if the command tree cannot be patched, the plugin logs why, restores the vanilla commands and disables itself.

## Limits

### Not supported

| Software and versions | What happens |
| --- | --- |
| Spigot 1.21.11 and 26.x | Not supported yet; use Paper or a Paper fork. These servers replace the numeric operator level with Minecraft's `PermissionSet`, which the `spigot` adapter does not handle yet. The plugin logs the SEVERE line `Could not add permission checks`, restores the vanilla commands and disables itself; the server keeps running. |
| Spigot 1.20.5 - 1.21.10 | Untested. The `spigot` adapter has name-table rows for these versions but they were never booted, so the startup warning appears. The plugin may work. |
| Folia before 1.20.6 | Not supported. The Paper lifecycle command API required by the adapter is unavailable. |
| Minecraft before 1.8.8 | Not supported. |

### Features

- **Bypasses:** the force-gamemode bypass is not available on Spigot 1.13+ and Paper 1.13.2; the move-speed bypass is not available on Spigot 1.13+ and Paper 1.13.2 - 1.19.4. Both need Paper's events.
- **Selectors:** random selectors (`@r`, `sort=random`) are checked against every entity they could pick, because the server draws again when the command runs. On 1.13+, selectors in `/execute` forks (`as`, `at`) are not checked per fork. See [Selectors](permissions.md#selectors).
- **Upstream nodes not added**, because the server has no API for them: `minecraft.bypass.spawn-protection`, `minecraft.bypass.chat-speed`, `minecraft.bypass.move-speed.vehicle.<entity>`, `minecraft.debug_chart`, `minecraft.<query|load>.<entity|block>`, `minecraft.adminbroadcast.receive` (use Paper's `minecraft.admin.command_feedback`) and the operator block `edit` action.

### Runtime

- **Disabling at runtime:** if a plugin manager disables this plugin while the server keeps running (Spigot and Paper before 1.20.6), the plugin re-syncs commands to remove its checks from the `minecraft:` copies. It skips the re-sync only when the server is known to be stopping; an unavailable shutdown probe does not prevent the re-sync. Enabling or disabling the plugin while players are online may race the server's asynchronous command-tree send and cause a command-list update to fail.
- **Console feedback:** with `/execute feedback console` the console may show a result twice (the direct output plus the admin log line).

### 1.8.8 - 1.12.2

- Only ten commands have sub-nodes ([list](permissions.md#188---1122-legacy-servers)); all others keep `minecraft.command.<name>`.
- 1.8.8 has no tab completion filtering; denied completions are refused when run.
- Commands run by other plugins (`performCommand`, `dispatchCommand`) and commands run by clicking signs are not checked. `commands.yml` aliases are.
- Selectors inside `/execute` are resolved from each target's position; if any execution coordinate is not exactly `~` and the inner command (after alias expansion) uses a selector in a selector-capable argument, the command is denied with the normal no-permission message.
- The move-speed bypass does not cover vehicle moves.
- The `view` deny of operator blocks is only partly enforced on 1.8.8 command blocks and 1.12.2 structure blocks: part of it is client-side, and edit packets still pass.

## Other server software

| Software | Status |
| --- | --- |
| Paper | Supported (1.8.8 through 26.3) |
| Spigot, CraftBukkit-based builds | Supported 1.8.8 through 1.20.4; see [Limits](#limits) for later versions |
| Purpur | Supported: 1.20.6 through 26.3, every version Purpur published (see below) |
| Pufferfish | Supported on 1.21 through 1.21.10 (see below) |
| Folia | Supported from 1.20.6; four boot-tested builds are listed [above](#folia). |

### Purpur and Pufferfish builds

Both are Paper forks and use the `paper` adapter. Purpur 1.20.6 through 26.3 (every version Purpur published) and Pufferfish 1.21 through 1.21.10 were booted with the plugin and LuckPerms 5.5.87 when support was added, and each installed the permission checks with no plugin errors and no untested-version warning. The builds change often, so the repository does not list them; the boot matrix in `.github/workflows/build.yml` is what is checked on every push.

- **Purpur** (`api.purpurmc.org/v2/purpur`) has no build for 1.21.2 (as with Paper) or 26.1.1, which Paper does have. Java requirements match Paper's.
- **Pufferfish** publishes only through its Jenkins (`ci.pufferfish.host`, job `Pufferfish-1.21`); it has builds for 1.21, 1.21.1, 1.21.3, 1.21.7, 1.21.8 and 1.21.10 only.
- **CI** boots Purpur 1.20.6, 1.21.11, 26.1.2, 26.2 and 26.3 and Pufferfish 1.21.1 and 1.21.10 on every push and pull request: the newest build of each Purpur minor line, and the oldest and newest Pufferfish release in range. The other versions were booted by hand with `scripts/compat-smoke.sh --software purpur|pufferfish`.

Paper 1.20.5 (build 22) runs with Mojang names but has no lifecycle `COMMANDS` event, so it is served by the `spigot` adapter.

## Permissions plugins

See [Permissions plugins](providers.md). CI runs LuckPerms 5.5 on Java 11 and later and LuckPerms 5.4.158 on Java 8; LuckPerms 5.5 needs Java 11 or later.

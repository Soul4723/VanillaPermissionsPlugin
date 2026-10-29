# Architecture

A map of the code for contributors: how one jar serves every Bukkit-family server from Minecraft 1.8.8 to 26.3, and where to look when you change something. Which versions are supported and tested is in [Compatibility](../compatibility.md).

## Overview

- **Maven multi-module reactor.** Shipped bytecode is Java 8, except the `paper` adapter (Java 21), which only loads on Java 21+.
- **No compile-time dependency on server internals.** Internals (the NMS command dispatcher, selector and operator-level classes) are reached by reflection, with per-version name tables where needed. No NMS, BuildTools or paperweight is needed to build.
- **One jar, three adapters.** The plugin chooses the adapter at startup from the server's classes, never from the version string.
- **Permission decisions** all go through `PermissionManager` in `core`, so adapters never talk to LuckPerms or Vault directly.

## Module layout

```text
pom.xml     vanillapermissions-parent: aggregator, shared versions and plugin configuration
core/       Java 8   Spigot API 1.8.8, LuckPerms API, Vault API
brigadier/  Java 8   Spigot API 1.13.2, Brigadier 1.0.17: tree patching and listeners shared by the 1.13+ adapters
paper/      Java 21  Paper API 1.20.6: Paper and Folia 1.20.6+ and forks
spigot/     Java 8   Spigot 1.13+, Paper 1.13 - 1.20.5
legacy/     Java 8   Spigot and Paper 1.8.8 - 1.12.2
plugin/     plugin.yml; shades every module into target/VanillaPermissionsPlugin-<version>.jar
```

`core` is compiled against the oldest API in scope (Spigot 1.8.8), so a call that does not exist on 1.8 is a compile error instead of a `NoSuchMethodError` on someone's server. Paper-only calls are not allowed there.

Packages under `io.github.soul4723.vanillapermissions` in `core`:

| Package | Contents |
| --- | --- |
| (root) | `VanillaPermissionsPlugin`: the main class. Warns on untested versions, selects the adapter and the permission provider, registers `/vanillapermissions` and the version-independent listeners, then hands over to the adapter. |
| `util` | The permission model: `PermissionProvider` with LuckPerms and Bukkit implementations, `VaultHook`, `PermissionManager`, `ServerSoftware`, and `SupportedVersions` (the tested versions per software, which must match the boot matrix in `build.yml`). |
| `platform` | The adapter extension point: `VersionAdapter`, `AdapterCandidate`, `Adapters`, `ServerVersion`, `AdapterLog` (log text shared by every adapter and by `scripts/compat-smoke.sh`) and `PluginScheduler` (Folia). |
| `selector` | `SelectorRules` and `SelectorDenial`: scope, limit and weight rules on already-resolved targets, without Brigadier. Each adapter resolves its own targets and turns a denial into its own error. |
| `listener` | Whitelist and LuckPerms pre-login bypasses; shared operator-block checks. |
| `command` | `/vanillapermissions`. |

The adapters:

| Adapter | Covers | Reaches vanilla commands through |
| --- | --- | --- |
| `paper` | Paper and Folia 1.20.6+, and forks | Paper's `LifecycleEvents.COMMANDS`; Mojang names at runtime. |
| `spigot` | Spigot 1.13+, Paper 1.13 - 1.20.5 | `CraftServer#getServer()`, then CraftBukkit's `vanillaCommandDispatcher` field and the Brigadier `CommandDispatcher` inside it (found by type), patched in `onEnable`. Uses the name table `SpigotNames` for Spigot's obfuscated members. |
| `legacy` | Spigot and Paper 1.8.8 - 1.12.2 | No dispatcher: `CommandListener` handles `PlayerCommandPreprocessEvent` for labels that resolve to CraftBukkit's `VanillaCommandWrapper`, with hand-written grammars (`legacy/.../grammar`) that produce the modern node names. |

`brigadier` holds the Brigadier-generic half of the checks: `BrigadierPermissions` (install and rollback), `VanillaTree`, requirement and executor wrappers, `GameModeLiterals`, `ExecuteExtensions` and `SelectorTargets` (resolved selector arguments checked against `core`'s `SelectorRules`). `paper` and `spigot` supply the server-specific half by implementing `BrigadierBridge` (`PaperBridge`, `SpigotBridge`). Listeners shared by 1.13+ adapters (`DebugStickListener`, `OperatorBlockListener`, `ProviderReselectListener`) live in `brigadier/.../listener`.

## Choosing the adapter at runtime

`Adapters.select` walks an ordered list of `AdapterCandidate`s (id, minimum Java version, adapter class name, probe), most specific first:

| Order | Id | Minimum Java | Probe |
| --- | --- | --- | --- |
| 1 | `paper` | 21 | `LifecycleEvents.COMMANDS` exists and is a static field |
| 2 | `spigot` | 8 | CraftBukkit server, not Folia, and `com.mojang.brigadier.CommandDispatcher` loads through the server's class loader |
| 3 | `legacy` | 8 | Not Folia; CraftBukkit with no Brigadier in the server's class loader, and `VanillaCommandWrapper` present |

- A candidate is skipped when the JVM is older than its minimum Java version, so a Java 21 class is never loaded on Java 8.
- Probes only call `Class.forName`/`getField` on server classes, through the **server's** class loader (the plugin's loader also sees classes other plugins bundle). The first match is loaded by class name.
- If nothing matches, or the matched adapter cannot be instantiated, the plugin logs one SEVERE line naming the software and version and disables itself. An adapter that loads but cannot patch the tree restores the vanilla commands and disables the plugin.

## Extension points

```java
public interface VersionAdapter {
    String id();
    void enable(JavaPlugin plugin) throws Exception;
    void disable();
    int checkedNodeCount();
}
```

- `enable` is called from `onEnable` after the permission provider is chosen. Install the checks (now, or from an event that runs before the server sends command trees), register the adapter's listeners, and log `Added permission checks to <n> vanilla command nodes` or fail with the `Could not add permission checks` prefix (`AdapterLog`; `CompatSmokeLogPatternsTest` keeps the smoke script in step).
- `disable` restores every vanilla node or wrapper and must be safe if `enable` failed or never ran.
- `checkedNodeCount` is the number of vanilla nodes carrying a check (for `legacy`, the node paths its grammars can produce); `0` before install or after a failure.
- Permission queries can come from worker threads (Paper 1.16.5+ builds command trees asynchronously), so providers must be thread-safe.

To add an adapter: create the module (depending on `core`, and `brigadier` for a Brigadier server), implement `VersionAdapter` with a public no-argument constructor, add an `AdapterCandidate` to `Adapters.candidates()`, add the module to `plugin`'s dependencies so it is shaded in, and add rows to the boot matrix in `build.yml`.

`BrigadierBridge` is everything the shared tree patcher needs from the server (reading the dispatcher, command sources and operator levels, resolving selector arguments, bypassing vanilla's selector check, `/execute feedback` and `oplevel`). Native objects are plain `Object`s, so `brigadier` links no server class. Read the interface's Javadoc before implementing it.

## Notes per adapter

### `paper`

- Folia is detected through `RegionizedServer` (never initialised). The `PluginScheduler` seam runs per-player work on the entity scheduler and global work on the global scheduler. Folia before 1.20.6 is refused rather than falling through to `spigot`.

### `spigot`

- **One reflection adapter for every version.** `SpigotNames` is a name table with rows from `v1_13_R1` to `v1_21_R7` plus Mojang names; it is validated at install and the adapter fails closed if a row is missing. Where a member's signature is unique, a structural lookup (by parameter and return types) is used instead.
- **Mapping generation** is decided once by `MappingGeneration`: the `vX_Y_RZ` package token up to 1.16.5, a class probe from 1.17 (Spigot names or Mojang names). Paper 1.20.5 and Spigot 26.x use Mojang names.
- **Operator levels** are an `int` before 1.21.11 and a `PermissionSet` from 1.21.11. `NativeAccess` probes which shape the server has and fails the install on an unknown one.
- **Timing is `onEnable`:** CraftBukkit registers its `VanillaCommandWrapper`s and syncs commands after `POSTWORLD` plugins enable, so wrapping then reaches the live tree and the tree sent to clients. A late install (PlugMan, `/reload`) would need `CraftServer#syncCommands()`.
- **Selector override:** Paper's `bypassSelectorPermissions` is inverted on 1.19.4 - 1.20.4, so this adapter instead clears the parsed selector's "uses selector" flag and restores it in `finally`.
- **Final field:** Spigot's `CommandNode.requirement` is `private final`; `Field#set` after `setAccessible(true)` works through Java 25. If a later JDK refuses it, the fallback is rebuilding the node with Brigadier's public constructors.
- **Node counts** are much lower on old versions, so boot-matrix rows carry their own `min_nodes` (the script's default is 1000).
- **Paper-only bypasses** (force-gamemode, move-speed) are registered by class name in `PaperEventBypasses` where the events exist.

### `legacy`

- **Interception, not patching.** The wrappers in CraftBukkit's `knownCommands` must not be replaced: command-block and `/execute` dispatch test `instanceof VanillaCommandWrapper`, and a swapped `kill` made a command block's `kill @e[type=pig]` do nothing on 1.12.2.
- **Ten grammars** (`gamemode`, `time`, `weather`, `kill`, `effect`, `give`, `clear`, `tp`/`teleport`, `execute`, `scoreboard`) parse the typed arguments and yield the modern node names; `LegacyVersion` only changes which node paths a grammar lists for 1.8 - 1.12 (for example `time.query.day` from 1.12); parsing is the same on every version. The node lists are in [Compatibility](../compatibility.md#188---1122).
- **Selectors** are resolved with NMS `PlayerSelector` after `MinecraftServer.worldServer` is filled (`LegacyServer`), then checked by `core`'s `SelectorRules`. Anything it cannot resolve or parse is denied.
- **Tab completion** is filtered by `TabCompleteFilter` through `TabCompleteEvent` (1.9+; 1.8.8 has none).
- **Other features** live in `legacy/.../feature`: force-gamemode (restores the saved game mode at join), operator blocks and a Netty move-speed hook.

## Java targets

| Module | `--release` | Why |
| --- | --- | --- |
| `core`, `brigadier`, `spigot`, `legacy` | 8 | Must load on Java 8 servers (1.8.8 - 1.16.5 commonly run it) |
| `paper` | 21 | Paper 1.20.6+ requires Java 21 |
| tests (all modules) | 21 (`testRelease`) | JUnit 6 runs on JDK 21 |

- The build runs on **JDK 21**. `--release 8` checks against the real Java 8 class library, so `List.of` or `String.isBlank` in a Java 8 module fails to compile.
- A single jar holding Java 8 and Java 21 classes is safe because classes load lazily and the adapter's Java gate keeps the Java 21 classes from being reached on older JVMs.
- Dropping Java 8 later is a MAJOR version bump.

## Tests

| Layer | Where | What |
| --- | --- | --- |
| Unit | each module, JDK 21 | Permission model and providers (`core`); tree patching against a fake Brigadier dispatcher (`paper`, `brigadier`); name-table validation against fake servers of several generations (`spigot`); grammars and selectors (`legacy`) |
| Shared helpers | `core` test-jar | `TestLuckPerms` and proxy helpers reused by adapter modules |
| Packaging | `plugin` | `plugin.yml` filtering and declared nodes and adapter candidates (`PluginDescriptorTest`) |
| Boot | boot matrix in `build.yml`, via `scripts/compat-smoke.sh` | Each row boots a real server with the jar and LuckPerms and checks the success line and a minimum node count |
| Behaviour | [live tests](testing.md) | Commands, selectors and bypasses on a live server with a bot player |

Unit tests cannot run on a real Java 8 JVM (JUnit 6 needs Java 17+). Java 8 behaviour is covered by `--release 8` and by booting 1.8.8, 1.12.2 and 1.16.5 servers on Java 8 in the boot matrix.

## CI matrix

The boot matrix in `build.yml` has one row per software and version, with `java`, an optional pinned `build` and `min_nodes`. Rows are chosen per name-table boundary: the newest patch of each Minecraft minor, plus every version where a table changes. `SupportedVersions` must list the same versions (`SupportedVersionsTest` checks it).

Spigot cannot be downloaded, so the `spigot-jar` job builds each needed revision once with a pinned BuildTools and caches it (key `spigot-<version>-bt<build>`); the jars are never uploaded as artifacts or release assets. See [Releasing](releasing.md#ci) for the workflows.

## Release artifacts

One jar, `VanillaPermissionsPlugin-<version>.jar`, for every supported server, with its `.sha256`. The adapter is chosen at startup. `plugin` uses `maven-shade-plugin` to merge `core` and every adapter and writes the jar to the root `target/`. The manifest keeps `paperweight-mappings-namespace: mojang`, which Paper 1.20.5+ reads to leave the jar unremapped (the `paper` adapter needs Mojang names); older servers ignore it.

## `api-version`

The jar declares `api-version: '1.13'` in `plugin.yml`, the lowest value every 1.13+ server accepts; it only avoids CraftBukkit's legacy material remapping, and the plugin still works before 1.13 (the key is ignored there). `folia-supported: true` sits in the same descriptor and needs no Folia-only jar. Paper 1.20.5+ requires the value not to be newer than the server.

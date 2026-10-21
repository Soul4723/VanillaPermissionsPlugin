# Architecture

Maintainer and contributor notes: how one jar serves every Bukkit-family server from Minecraft 1.8.8 to 26.3 (Paper, Folia, Spigot, Purpur, Pufferfish), and why it is built this way. Which versions are supported and tested is in [Compatibility](../compatibility.md).

## Summary

| Decision | Choice |
| --- | --- |
| Build tool | Maven, multi-module reactor |
| Modules | `core` (Java 8, Spigot 1.8.8 API), `brigadier` (shared by the 1.13+ adapters), one adapter module per server-version group (`paper`, `spigot`, `legacy`), `plugin` (packaging) |
| How server internals are reached | Reflection with per-version name tables; no NMS, BuildTools or paperweight at build time |
| Adapter choice | Ordered candidate list in `core`, each with a minimum Java version and a class-presence probe; the first match is loaded by class name |
| Shipped artifacts | One jar, `VanillaPermissionsPlugin-<version>.jar`, at the root `target/`; the adapter is chosen at startup |
| Java | Shipped bytecode is Java 8 except the modern Paper adapter (Java 21), which is loaded only on Java 21+ |
| Tests | Unit tests on JDK 21 for every module (`testRelease` 21); real-JVM coverage from server boots in CI |
| CI | `compat.yml` boot matrix with a row per software and version; Spigot jars built once with BuildTools and cached |
| `api-version` | `'1.13'` |

## Module layout

```
pom.xml          vanillapermissions-parent: aggregator, shared versions and plugin configuration
core/            vanillapermissions-core      Java 8   Spigot API 1.8.8, LuckPerms API, Vault API
brigadier/       vanillapermissions-brigadier Java 8   Spigot API 1.13.2, Brigadier 1.0.17: tree patching and the 1.13+ listeners shared by every 1.13+ adapter
paper/           vanillapermissions-paper     Java 21  Paper API 1.20.6: Paper 1.20.6+ and forks
spigot/          vanillapermissions-spigot    Java 8   Spigot 1.13+, Paper 1.13 - 1.20.5
legacy/          vanillapermissions-legacy    Java 8   Spigot and Paper 1.8.8 - 1.12.2
plugin/          vanillapermissions-plugin             plugin.yml; shades every module into ../target/VanillaPermissionsPlugin-<version>.jar
```

### `core`

Everything that must load on every supported server, so it is compiled with `--release 8` against the oldest API in scope, `org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT`. Compiling against the oldest API turns "this call does not exist on 1.8" into a compile error instead of a `NoSuchMethodError` on someone's server.

- `io.github.soul4723.vanillapermissions.VanillaPermissionsPlugin`: the plugin main class. It warns on untested versions, selects the adapter, chooses the permission provider, registers `/vanillapermissions` and the version-independent listeners, then hands over to the adapter.
- `util`: the permission model. `PermissionValue`, `PermissionProvider` and its LuckPerms and Bukkit implementations, `VaultHook`, `PermissionManager`, `ServerSoftware` (Paper, Spigot, Purpur, Pufferfish, ...) and `SupportedVersions`, which keeps one tested-version list per software for the startup warning and `/vanillapermissions debug`. These are platform-free apart from the Bukkit `Player`/`Plugin` types.
- `platform`: the extension point (see [Extension points](#extension-points)): `VersionAdapter`, `AdapterCandidate`, `Adapters`, `ServerVersion` and `AdapterLog` (the success and failure log text every adapter shares, which `CompatSmokeLogPatternsTest` keeps in step with `scripts/compat-smoke.sh`). `ServerVersion` calls Paper's `Bukkit.getMinecraftVersion()` by reflection when the method exists (it is not in the 1.8.8 API); otherwise it takes `Bukkit.getBukkitVersion()` up to the first `-`.
- `selector`: `SelectorRules` and `SelectorDenial`, the selector rules (scope, limit, weight) on already-resolved targets, without Brigadier. Each adapter resolves its own targets and turns a `SelectorDenial` into its own error: `brigadier` into a `CommandSyntaxException`, `legacy` into a chat message.
- `listener`: `WhitelistBypassListener` and `LuckPermsPreLoginListener`, which only use 1.8 API.
- `command`: `/vanillapermissions`.

Paper-only calls are not allowed in `core`. `getPluginMeta()` became `getDescription()`, which Paper 26.3 still has.

### Adapter modules

An adapter covers one group of servers that share a way of reaching vanilla commands. It owns everything that differs by server version: reaching the command tree, the command-source and selector internals, and the listeners for events that only exist in its range.

| Adapter | Covers | Command system | Reaches the dispatcher through | Java |
| --- | --- | --- | --- | --- |
| `paper` | Paper 1.20.6+ and forks | Brigadier, Mojang names at runtime | Paper's `LifecycleEvents.COMMANDS` | 21 |
| `spigot` | Spigot 1.13+; Paper 1.13 - 1.20.5 | Brigadier, Spigot names: classes in `net.minecraft.server.vX_Y_RZ` up to 1.16.5; from 1.17, Spigot class names in Mojang-style packages (`net.minecraft.commands.CommandListenerWrapper`, not Mojang's `CommandSourceStack`) with obfuscated members, still so on 1.21.11 (`v1_21_R7`); 26.x uses Mojang names. Operator levels are `PermissionSet` from 1.21.11 | `CraftServer#getServer()` → the CraftBukkit field `MinecraftServer#vanillaCommandDispatcher` → the Brigadier `CommandDispatcher` field inside it, found by type; wrapped in `onEnable` | 8 |
| `legacy` | Spigot and Paper 1.8.8 - 1.12.2 | Pre-Brigadier `ICommand`, run through CraftBukkit's `VanillaCommandWrapper` | No dispatcher: `PlayerCommandPreprocessEvent`, acting only when the label resolves to a `VanillaCommandWrapper`, with hand-written grammars that produce the modern node names | 8 |

Paper 1.20.5 runs with Mojang names but has no `LifecycleEvents.COMMANDS`, so it takes the `spigot` adapter. That works: the dispatcher path uses no obfuscated names and the name table carries the Mojang names. See [Spigot 1.20.5 and later](#spigot-1205-and-later).

`brigadier` holds the Brigadier-generic half of the command checks: `BrigadierPermissions` (install and rollback), `VanillaTree` (walking the tree, from the `minecraft:` aliases or from a vanilla-only dispatcher), the requirement and executor wrappers, `GameModeLiterals`, `ExecuteExtensions`, and `SelectorPolicy`, which wraps `core`'s `SelectorRules` for Brigadier. Both `paper` and `spigot` depend on it and supply the version-specific half through the **bridge**, `BrigadierBridge` (see [Extension points](#extension-points)). `brigadier` and `spigot` compile against `spigot-api` 1.13.2 and Brigadier 1.0.17.

Terms used below: `PermissionPredicate` is the wrapper that replaces a node's requirement with a permission check that falls back to the vanilla requirement when the node is unset. The selector executor wraps a node's command to check `@` selector permissions before the vanilla command runs. `SelectorPolicy` holds the selector rules (scopes, limits, weights). The **selector bypass** (`bypassSelectorCheck` in the bridge) makes vanilla skip its own selector check while the plugin's checks run: Paper sets `bypassSelectorPermissions`, `spigot` toggles the parsed selector's "uses selector" flag.

Listeners, by where they live:

| Where | Listeners |
| --- | --- |
| `core` | `WhitelistBypassListener`, `LuckPermsPreLoginListener` |
| `brigadier` (`listener`, registered through `BrigadierFeatures.registerListeners`) | `DebugStickListener`, `OperatorBlockListener`, `ProviderReselectListener` |
| `paper` (`listener`) | `ForceGamemodeBypassListener`, `MoveSpeedBypassListener` (Paper-only events, Paper 1.20.6+). The `spigot` adapter's `PaperEventBypasses` registers the same two bypasses by class name where Paper's events exist (force-gamemode on Paper 1.16.5+, move-speed on Paper 1.20.4+); Spigot and Paper 1.13.2 have neither |
| `legacy` | `CommandListener` (command interception), `TabCompleteFilter`, and in `feature`: `ForceGamemodeBypassListener`, `OperatorBlockListener`, `MoveSpeedBypass` (Netty hook), `PlayerDataReader` |

### `plugin`

`plugin` holds `plugin.yml` and uses `maven-shade-plugin` to merge `core` and every adapter into one jar. The jar is written to the root `target/`, so `build.yml`, `compat.yml` and `release.yml` do not depend on the module layout. ViaVersion writes its universal jar to the root `build/libs` in the same way. The jar keeps the manifest entry `paperweight-mappings-namespace: mojang`. Paper 1.20.5+ reads it and leaves the jar unremapped, which the `paper` adapter needs. Older Paper and Spigot ignore it, which is right for the `spigot` and `legacy` adapters too, since they look names up per version themselves.

## Choosing the adapter at runtime

`platform.Adapters.select` walks an ordered list of `AdapterCandidate`s. Each candidate is an id, a minimum Java version, an adapter class name and a probe. For each candidate:

1. It skips the candidate if the running JVM is older than its minimum Java version. `java.specification.version` is `1.8` on Java 8 and `21` on Java 21. Loading a Java 21 class on an older JVM fails with `UnsupportedClassVersionError`; this gate means it is never attempted.
2. It runs the probe. Probes live in `core` and only use `Class.forName` and `getField` on server classes, never the adapter's own classes.
3. It loads the first match by name and instantiates it (with a public no-argument constructor). A probe that throws is logged and moves on to the next candidate. If instantiation fails (`LinkageError` or reflection failure), the plugin logs it and uses no adapter rather than falling through to a less specific one.

The order is most specific first. As committed in `core/.../platform/Adapters.java`:

| Order | Id | Minimum Java | Probe |
| --- | --- | --- | --- |
| 1 | `paper` | 21 | `LifecycleEvents.COMMANDS` exists and is a static field |
| 2 | `spigot` | 8 | The server object is CraftBukkit's (`org.bukkit.craftbukkit.…`), it is not Folia, and `com.mojang.brigadier.CommandDispatcher` loads through **the server's own class loader** |
| 3 | `legacy` | 8 | Not Folia; CraftBukkit, with no Brigadier in the server's class loader, and `<craftbukkit package>.command.VanillaCommandWrapper` present |

The `spigot` and `legacy` probes use the server's class loader, not the plugin's, because the plugin's loader also sees classes that other plugins bundle, and a 1.8 - 1.12 plugin may ship Brigadier unrelocated. Folia is excluded from both so that a Folia without the lifecycle API is never treated as Spigot. If nothing matches, or the matched adapter cannot be instantiated, the plugin logs one SEVERE line naming the server software and version, then disables itself.

A second safety net: an adapter that loads but cannot patch the tree restores vanilla commands and disables the plugin.

A version string never decides which adapter runs; probes do. That lets an untested newer server still work (CommandAPI's `CommandAPIVersionHandler`, by contrast, switches on the version string and fails on unknown versions unless `fallbackToLatestNMS` is set). Inside an adapter, a version-keyed name table is fine (see below).

Prior art for this design:

- **WorldEdit's `BukkitImplLoader`**: try each adapter by class name and skip failures. WorldEdit scans its jar for adapter classes; an explicit list is simpler for three adapters.
- **Citizens2's `NMS.loadBridge`** (`main/src/main/java/net/citizensnpcs/util/NMS.java`) and **Geyser's `SpigotAdapters`**: load the adapter by class name, so that no other adapter is ever linked.
- **lucko/commodore** and **LuckPerms** (`isBrigadierSupported`): probe for Brigadier with `Class.forName("com.mojang.brigadier.CommandDispatcher")`.
- **LuckPerms' `checkIncompatibleVersion`**: on a server it cannot run on, log a clear message and disable.
- **spark**: a different `--release` per module (`spark-bukkit` at 8, `spark-paper` at 21). Here both levels share one jar, as Geyser does for its adapters (Java 8 base, 17 or 21 per version).

## Reaching server internals

The plugin only compiles against public artifacts: the Bukkit, Spigot and Paper APIs, and Mojang's Brigadier library from `libraries.minecraft.net`. Server internals are always reached by reflection.

- **Modern Paper** (1.20.5+) runs with Mojang names, so `paper` looks classes and members up by their Mojang name.
- **Spigot up to 1.21.11 and Paper up to 1.20.4** run with Spigot-obfuscated members, and classes are in `net.minecraft.server.vX_Y_RZ` up to 1.16.5. `spigot` uses one **name table**, `SpigotNames` (15 classes and 21 members, with rows from `v1_13_R1` to `v1_21_R7` plus Mojang names, generated by joining Mojang's server mappings with Spigot's BuildData). The table is validated at install and the adapter fails closed if a row is missing.
  - Where a member's signature is unique, a **structural lookup** is used instead (find the method by parameter and return types), so a renamed member does not break it. This follows ProtocolLib's `FuzzyReflection` (`getMethodByReturnTypeAndParameters`, `getFieldByType`), ViaVersion's lookup of `SharedConstants` by signature, and commodore's return-type lookups.
  - The tables follow NBT-API's `ClassWrapper` and `ReflectionMethod` enums, which give each name the version it applies from (`new Since(MC1_18_R1, "putInt(...)")`).
  - The mapping generation is decided **once**, and every lookup reads that one value. Up to 1.16.5 it is the `vX_Y_RZ` token from the CraftBukkit package name (`Bukkit.getServer().getClass().getPackage().getName()`). From 1.17 a class probe decides between Spigot and Mojang names: PacketEvents sets `IS_OBFUSCATED` from whether `net.minecraft.server.network.PlayerConnection` exists, and ProtocolLib's `isMojangMapped()` checks for `ServerLevel`. Lookup helpers take both names where they differ, like CommandAPI's `SafeVarHandle.ofOrNull(cls, spigotName, mojangName, type)`. `MappingGeneration` makes the decision once.
- **Pre-1.13** (`legacy`): nothing is patched. The adapter reads the command map through the Bukkit API (plus CraftBukkit's `getCommandMap()` by reflection), and only reaches NMS for selector resolution (`PlayerSelector`), the saved game mode and the move-speed Netty hook. See [The legacy adapter](#the-legacy-adapter).

### Compiling against server internals is not used

CommandAPI compiles its Spigot modules against `org.spigotmc:spigot:<ver>:remapped-mojang` with a SpecialSource remapping task and its Paper modules with paperweight-userdev; Citizens2 compiles each `vX_RN` module against Spigot jars built with BuildTools; WorldEdit, FastAsyncWorldEdit and Geyser's `geyser-adapters` compile against paperweight dev bundles. Any of these needs either BuildTools jars on every build machine or Gradle with paperweight (1.17.1+ only), plus a remapping step for Spigot. This plugin touches a handful of members per version (the dispatcher, the sender, the permission level, a few argument resolvers), so reflection with tables is much less machinery. It also keeps BuildTools out of `mvn verify`: BuildTools is only needed to *run* Spigot in the CI boot matrix.

## Build tool: Maven

Maven covers what this layout needs:

- a per-module `maven.compiler.release` (8 or 21);
- `maven.compiler.testRelease` 21;
- `test-jar` sharing of test helpers;
- `maven-shade-plugin` for the single jar.

Citizens2 uses the same pattern on Maven: a `main` module at `<release>8</release>`, per-version modules, and a `dist` module that unpacks them into one jar (`dist/pom.xml`, maven-assembly-plugin). paperweight-userdev, the main reason to move to Gradle, is not needed because nothing is compiled against server internals.

## Java targets

| Module | `--release` | Why |
| --- | --- | --- |
| `core` | 8 | Must load on every server; 1.8.8 - 1.16.5 servers commonly run Java 8 |
| `brigadier`, `spigot`, `legacy` | 8 | Spigot and Paper 1.13 - 1.16.5 run on Java 8 |
| `paper` | 21 | Paper 1.20.6+ requires Java 21 |
| tests (all modules) | 21 (`testRelease`) | JUnit runs on JDK 21 in CI |

- The build runs on **JDK 21**. Its `javac` accepts `--release 8` with an "obsolete" warning. spark and PacketEvents build `--release 8` on JDK 25, so a later build JDK is not blocked.
- `--release 8` checks the Java API against the real Java 8 class library (`ct.sym`), not just the bytecode level. So `List.of` or `String.isBlank` in `core` fails to compile.
- A single jar holding Java 8 and Java 21 classes is safe because the JVM loads classes lazily: a class is only loaded when first used, and the adapter's Java gate makes sure the Java 21 classes are only reached on Java 21+. Geyser ships adapters of mixed levels the same way.
- Java 8 stays because many 1.8.8 - 1.16.5 servers run it. spark, PacketEvents, Citizens2 and NBT-API still ship Java 8 bytecode; LuckPerms moved to Java 11, and ViaVersion, ProtocolLib and CommandAPI target 17. Dropping Java 8 later is a MAJOR version bump.

## Tests

Every module's tests are compiled with `testRelease` 21 and run on JDK 21, including the tests of Java 8 modules. JUnit 6 needs Java 17+ at runtime, but it can test classes compiled with `--release 8` (the test JVM simply loads older bytecode). This rules out running unit tests on a real Java 8 JVM, which this design does not need:

- Java 8 API misuse is caught at compile time by `--release 8`.
- Behaviour on a real Java 8 JVM is covered by booting 1.8.8, 1.12.2 and 1.16.5 servers on Temurin 8 in `compat.yml`.

| Layer | Where | What |
| --- | --- | --- |
| Unit | each module, JDK 21 | Permission model and providers (`core`); tree patching against a fake Brigadier dispatcher built in the test (`paper`, `brigadier`); name-table validation against the real-shaped `v1_16_R3` fake server plus focused nested fake argument/selector cases, a 1.21.11-shaped fake source and `PermissionSet` classes (`spigot`); grammar and selector tests (`legacy`) |
| Shared helpers | `core` test-jar | `TestLuckPerms` and proxy helpers, reused by adapter modules |
| Packaging | `plugin` | `plugin.yml` filtering and declared nodes (`PluginDescriptorTest`, `BypassNodesDeclaredTest`) |
| Boot | `compat.yml` via `scripts/compat-smoke.sh` | Each matrix row boots a real server with the jar and LuckPerms and checks the success line and node count |
| Behaviour | the [live tests](testing.md), recorded in [Compatibility](../compatibility.md) | Commands, selectors, bypasses on a live server with a client, for at least one version per adapter |

Every adapter must log the same success line, `Added permission checks to <n> vanilla command nodes`, and the same failure prefix, `Could not add permission checks`, so that the smoke script and `CompatSmokeLogPatternsTest` work unchanged. The expected minimum count differs by version range (797 nodes on Spigot 1.13.2 against 1867 on Paper 1.20.6 and 2577 on 26.3), so every matrix row carries its own `min_nodes`, which the script reads as `MIN_NODES`. On `legacy` the count is the number of node paths its grammars can produce, not vanilla tree nodes.

## CI matrix

`compat.yml` keeps one job per row. Rows have two more fields, `software` (`paper`, `spigot`, `purpur`, ...) and `java`. Rows are chosen per **name-table boundary**, not per patch release: the newest patch of each Minecraft minor, plus every version where a table changes.

| Software | Range | Server jar | Java |
| --- | --- | --- | --- |
| Paper | 1.8.8 - 26.x | Paper's download API (`fill.papermc.io/v3/projects/paper`), pinned build | 8 up to 1.16.5, 17 for 1.17.x - 1.20.4, 21 for 1.20.5 - 1.21.11, 25 for 26.x |
| Spigot | 1.8.8 - 26.3 | BuildTools, cached (below) | 8 up to 1.16.5, 17 for 1.17.1 - 1.20.4, 21 for 1.20.6 - 1.21.11, 25 for 26.3 |
| Folia, Purpur, Pufferfish | see `compat.yml` | their download APIs | same |

**Spigot jars in CI.** Spigot cannot be downloaded, so a `spigot-jar` job builds each needed revision once:

1. It runs `java -jar BuildTools.jar --rev <version> --compile spigot` with a pinned BuildTools build and the JDK that revision needs (Java 8 up to 1.16.5, 17 for 1.17.1 - 1.20.4, 21 for 1.20.6 - 1.21.11, 25 for 26.3).
2. It stores the result with `actions/cache`, keyed `spigot-<version>-bt<buildtools build>`.

Smoke rows restore that cache by key. The jars are never uploaded as artifacts or release assets, because they must not be redistributed. A cold build takes several minutes per revision, so after the first run the cache is what keeps the workflow fast. A twice-weekly `schedule` run keeps the cache entries from expiring (GitHub evicts entries unused for 7 days).

Prebuilt Spigot jars from a third-party mirror (`repo.codemc.io/repository/nms/`, as NBT-API's integration tests use through `tr-spigotmc-maven-plugin`) would be faster, but the mirror hosts jars Spigot does not distribute. BuildTools is used instead.

## Release artifacts

One jar, `VanillaPermissionsPlugin-<version>.jar`, for every supported server, with its `.sha256`. The adapter is chosen at startup.

## `api-version` across 1.8 - 26.x

| Server | What `api-version` does |
| --- | --- |
| 1.8 - 1.12 | Ignored; the key did not exist |
| Spigot 1.13 - 1.20.4 | Must be one of the server's known values (`1.13` ... its own version), else `Unsupported API version`. If missing, the plugin is treated as legacy (material remapping, a warning). From CraftBukkit's `CraftMagicNumbers.checkSupported`. |
| Paper 1.20.5+ and current Spigot | Must not be newer than the server; patch versions such as `1.20.6` are allowed |

The jar declares **`api-version: '1.13'`** (with LuckPerms' explanatory comment in `plugin.yml`), the lowest value every 1.13+ server accepts. `folia-supported: true` sits in the same descriptor and needs no Folia-only jar (LuckPerms does the same).

This is the common choice. LuckPerms (`bukkit/loader/src/main/resources/plugin.yml`), spark (`spark-bukkit`), Citizens2, CommandAPI's Spigot jar, ProtocolLib, PacketEvents, ViaVersion and Geyser all declare `1.13`. LuckPerms and spark add the comment that it only avoids CraftBukkit's legacy remapping and that the plugin still works before 1.13. The plugin uses no `Material` or other renamed API through a legacy path, so the lower value changes nothing on modern Paper.

## Extension points

### Every adapter

`core/src/main/java/io/github/soul4723/vanillapermissions/platform/`:

```java
public interface VersionAdapter {
    String id();
    void enable(JavaPlugin plugin) throws Exception;
    void disable();
    int checkedNodeCount();
}
```

| Method | Contract |
| --- | --- |
| `id()` | Short name for logs and `/vanillapermissions debug` (`paper`, `spigot`, `legacy`). |
| `enable(plugin)` | Called from `onEnable` after the permission provider is chosen and `/vanillapermissions` is registered. Install the command checks (now, or from an event that still runs before the server sends command trees: `paper` uses `LifecycleEvents.COMMANDS`, `spigot` installs directly because CraftBukkit syncs commands after `onEnable`), and register this adapter's listeners. Log the success line or the failure prefix (see [Tests](#tests)). If patching fails, restore vanilla commands and disable the plugin on the next tick, as `paper` does. |
| `disable()` | Restore every vanilla command node or wrapper. Must be safe if `enable` failed or never ran. |
| `checkedNodeCount()` | Number of vanilla command nodes that currently carry a check; for `legacy`, the nodes its grammars can produce. `0` before install or after a failure. |

To add an adapter:

1. Create the module, depending on `core` (and `brigadier`, for a Brigadier server).
2. Implement `VersionAdapter` with a public no-argument constructor. A Brigadier adapter implements `BrigadierBridge` and passes it to `BrigadierPermissions`.
3. Add an `AdapterCandidate` to `Adapters.candidates()` in the right order (most specific probe first).
4. Add the module as a dependency of `plugin`, so it is shaded in.
5. Add `compat.yml` rows.

All permission decisions go through `PermissionManager` (`queryPermission`, `hasPermissionOrFallback`, `getIntegerMeta`), so adapters never talk to LuckPerms or Vault directly. These calls can come from worker threads (Paper 1.16.5+ builds command trees asynchronously), so providers must be thread-safe.

### Brigadier adapters (`brigadier` module)

`BrigadierBridge` (`brigadier/src/main/java/.../brigadier/BrigadierBridge.java`) is everything the shared tree patcher needs from the server. `PaperBridge` (Mojang names, in `paper`) and `SpigotBridge` (name table, in `spigot`) implement it. Native objects (command sources, entities) are plain `Object`s, so `brigadier` links no server class. The interface follows CommandAPI's `NMS<CommandListenerWrapper>`. Bridge methods can be called off the main thread.

| Member | What it does |
| --- | --- |
| `prepare(dispatcher)` | Looks up what the other methods need before anything is changed; throwing refuses the install. |
| `vanillaRoots(dispatcher)` | The vanilla command nodes with their permission paths. `paper` uses `VanillaTree.fromMinecraftAliases` (its dispatcher also holds plugin commands); `spigot` uses `VanillaTree.allCommands` (CraftBukkit's `vanillaCommandDispatcher` holds only vanilla commands). |
| `sender(source)` | The Bukkit `CommandSender` behind a native source (`getBukkitSender()`). |
| `allowsSelectors(source)` | Vanilla's own selector check: level 2 or `minecraft.command.selector`. |
| `operatorLevel(sourceOrEntity)` | Operator level 0 - 4 of a source or entity (`paper` and `spigot` handle `PermissionSet` levels; `spigot` also handles int levels). |
| `withOperatorLevelAtMost(source, level)` | A copy of the source with its level capped; never raises it. |
| `selectorResolver(type)` | How to resolve an argument type that can hold selectors (entities, game profiles, score holders), or `null`. |
| `selectorCheckBypassed(source)` | Whether vanilla already skips its selector check (Paper's `bypassSelectorPermissions`). |
| `bypassSelectorCheck(context, argumentName)` | Makes vanilla skip its selector check while the plugin's checks and the command run; returns a `SelectorOverride` to restore afterwards. `paper` sets `bypassSelectorPermissions`; `spigot` toggles `EntitySelector`'s "uses selector" flag. |
| `selectorTarget(resolved)` | Default method: classifies one resolved value for `SelectorRules`. |
| `gameModeLiterals(dispatcher, modeArgument)` | Default method: the four `/gamemode` literals that replace the game mode argument (1.19.4+), built name-free by `GameModeLiterals.nameFree`. |
| `entityArgument()`, `entitiesArgument()` | Argument types for one entity and for any number of entities, used by the `/execute` extensions. |
| `entityResolver()`, `optionalEntitiesResolver()` | Resolve those arguments to native entities. |
| `withSuppressedOutput(source)` | `feedback silent`. |
| `redirectsFeedback()` | Whether `feedback console` and `feedback entity` can be offered (`spigot`: 1.17+). |
| `feedbackToServer(source)`, `feedbackToEntities(source, entities)` | `feedback console` and `feedback entity`. |

Online players' command lists are refreshed through Bukkit's `Player.updateCommands()`, outside the bridge.

## Folia

The `paper` adapter handles Folia from 1.20.6. `Adapters` probes `RegionizedServer` through the server class loader and prevents older Folia from falling through to `spigot`. The small `PluginScheduler` seam runs LuckPerms refreshes on each player's entity scheduler and group snapshots/failure-disable tasks on the global scheduler. This is [LuckPerms' scheduler split](https://github.com/LuckPerms/LuckPerms/blob/master/bukkit/src/main/java/me/lucko/luckperms/bukkit/FoliaSchedulerAdapter.java), with the direct Paper API calls used by [CommandAPI](https://github.com/CommandAPI/CommandAPI/blob/master/commandapi-platforms/commandapi-paper/commandapi-paper-core/src/main/java/dev/jorel/commandapi/CommandAPIPaper.java) and [ViaVersion](https://github.com/ViaVersion/ViaVersion/blob/master/bukkit/src/main/java/com/viaversion/viaversion/ViaVersionPlugin.java); no scheduler library or reflection is needed. Removed commands have no nodes, and region-local entity selection remains Folia's vanilla behaviour. See [Compatibility](../compatibility.md#folia).

## Brigadier era (1.13+)

Design notes for the `spigot` adapter:

- **One reflection adapter.** Reaching the tree, wrapping requirements and executors, the `minecraft:` aliases and the `/gamemode` literals use no obfuscated names. `CraftServer#getServer()` gives the `MinecraftServer`; the CraftBukkit field `vanillaCommandDispatcher` holds the vanilla `Commands`; the Brigadier `CommandDispatcher` is its only non-static field of that type. This is commodore's find-by-type idea. One `spigot` module with a name table covers every version; there are no per-version modules.
- **Timing is `onEnable`.** CraftBukkit registers its `VanillaCommandWrapper`s and runs `syncCommands` after `load: POSTWORLD` plugins enable. Wrapping in `onEnable` therefore reaches the live tree, the `minecraft:` clones and the tree sent to clients without a re-sync, and the wrappers survive `/minecraft:reload`. `ServerLoadEvent` is too late. A late install (PlugMan, `/reload`) would need `CraftServer#syncCommands()` (public from 1.16, private before; EssentialsX's `ReflSyncCommandsProvider` calls it).
- **Top-level nodes already exist.** CraftBukkit's `VanillaCommandWrapper` checks `minecraft.command.<name>` for each root command. The plugin's wrapper sits in front: an explicit value wins, unset falls through to CraftBukkit and vanilla. A non-op is refused by the Bukkit wrapper before Brigadier runs, so granting `minecraft.command.<name>` is needed to open a command, as on Paper.
- **About 15 obfuscated members remain**: operator-level reads and lowering, the selector checks and resolvers, `/execute feedback` and `oplevel`. 1.13.2 - 1.16.5 is one name set with four 1.13.2 overrides; 1.17 - 1.20.4 changes a handful (`hasPermission(int)` becomes `c(int)` in 1.19, the "uses selector" field moves from `o` to `p`). Several can be found by shape instead.
- **Selector override.** Paper's `bypassSelectorPermissions` is inverted on 1.19.4 - 1.20.4 (bytecode: `if (bypass || (usesSelector && !hasPermission(2, ...))) throw`), so the `spigot` adapter does not use it. It clears `EntitySelector`'s "uses selector" flag on the parsed selectors and restores it in `finally`, as CommandAPI does (`SafeVarHandle`, `checkPermissions`/`p`). The `paper` adapter's use of the Paper flag is verified live on 1.20.6.
- **Fewer nodes on old versions.** 797 nodes on 1.13.2, 1259 on 1.16.5, 1297 on 1.17.1, 1663 on 1.19.4 and 1741 on 1.20.4. `compat-smoke.sh`'s default `MIN_NODES` of 1000 would fail 1.13.2, so those rows have their own minimum (see [CI matrix](#ci-matrix)).
- **Compile classpath.** Java 8 bytecode against `spigot-api` 1.13.2 and Brigadier 1.0.17 (1.13.2 itself ships Brigadier 1.0.14).
- **Thread safety.** Paper 1.16.5+ builds the client command tree on worker threads, so requirement predicates, and therefore `PermissionProvider`, run off the main thread. LuckPerms and the Bukkit provider already allow this; any new provider must too.
- **Final field.** Spigot's `CommandNode.requirement` is `private final` (Paper's is public). `Field#set` after `setAccessible(true)` works on Java 8 - 25 (Spigot 26.3 runs on 25); newer JDKs are restricting final-field writes. Fallback: rebuild the node with Brigadier's public constructors and swap it into the parent, as CommandAPI's `renameLiteralNode` does.

## Before Brigadier (1.8.8 - 1.12.2)

Design notes for the `legacy` adapter:

- **What the server gives.** CraftBukkit's `VanillaCommandWrapper` checks only `minecraft.command.<legacy name>`; on 1.9+ vanilla's op check maps to the same node; selectors are all-or-nothing through `minecraft.command.selector`. Of 83 modern commands, 32 can get full sub-nodes, 7 partial, 17 already have everything they can have at the root, and 27 do not exist before 1.13.
- **Interception, not patching.** The `legacy` adapter listens to `PlayerCommandPreprocessEvent` and acts only when the label resolves to a `VanillaCommandWrapper`. A hand-written grammar per command parses the arguments and yields the same node names as the modern tree (`minecraft.command.gamemode.creative.target`; legacy spellings `c`, `1`, `01` are aliases of `creative`, never their own nodes). The wrappers in `knownCommands` must **not** be replaced: CraftBukkit's command-block and `/execute` dispatch tests `instanceof VanillaCommandWrapper`, and a swapped `kill` made a command block's `kill @e[type=pig]` do nothing on 1.12.2.
- **Selector resolution fails closed.** NMS `PlayerSelector` sees no worlds unless `MinecraftServer.worldServer` is filled as `dispatchVanillaCommand` does; without that, selectors resolve to nothing and the check would pass.
- **Tab completion** is filtered through `TabCompleteEvent` on 1.9+. 1.8.8 has no such event, so denied sub-commands still complete there but are refused when run.
- **Non-command features:** whitelist and player-limit bypasses work unchanged (they are in `core`). Force-gamemode works by restoring the saved `playerGameType` from playerdata at join. Move-speed needs a small Netty handler. Operator-block place and break work; view is partly client-side, and edit packets get through. Debug stick, jigsaw and the `/execute` additions do not apply.
- **Packaging.** A Java 8 server loads a jar that also carries Java 21 classes behind a version gate, and ignores `api-version: '1.13'`. Shared code is compiled with `--release 8`, so one jar serves these versions too.
- **Selector rules live in `core`.** `legacy` needs the same selector rules (scope, limit, weight, messages) without Brigadier. They return a denial reason, and `brigadier` turns the reason into a `CommandSyntaxException`.
- **Root names that differ:** `/tell` is `minecraft.command.tell` (modern root `msg`) and `/xp` is `minecraft.command.xp` (modern `experience`). The adapter also denies when the modern root is false, so a modern configuration keeps working.
- **LuckPerms 5.5 is Java 11 bytecode**, so LuckPerms users on these versions already run Java 11+. Java 8 stays the target for everyone else.

## Spigot 1.20.5 and later

- **Paper 1.20.5 works through the `spigot` adapter.** It has Mojang names but no `LifecycleEvents.COMMANDS`, so the `paper` probe fails and the `spigot` probe matches. The name table carries the Mojang names (`MappingGeneration.MOJANG`, detected by a class probe). Boot-tested (1784 nodes) and in `compat.yml`.
- **The selector override is the toggle, not Paper's flag**, on every `spigot` version, because `bypassSelectorPermissions` is inverted on Paper 1.19.4 - 1.20.4.
- **Spigot 1.21.11 and 26.x use the same adapter.** 1.21.11 still runs Spigot class names with obfuscated members (CraftBukkit package `v1_21_R7`). 26.x runs Mojang names and an unversioned `org.bukkit.craftbukkit` package. Both replace the integer operator level with Minecraft's `PermissionSet`. Mapping detection (`MappingGeneration`), dispatcher access (`CraftServerAccess`) and adapter selection are unchanged.
- **The operator-level shape is probed, not taken from the mapping generation.** The Mojang generation covers both shapes: Paper 1.20.5 has int levels, Spigot 26.x has `PermissionSet`. Each `SpigotNames` entry is tagged with a shape (`SpigotNames.Levels`: `ANY`, `INT`, `PERMISSION_SET`). `NativeAccess` checks whether the command source class has CraftBukkit's `hasPermission(int, String)`. If it does, it resolves the `INT` entries, otherwise the `PERMISSION_SET` ones, so servers before 1.21.11 never load `PermissionSet` classes.
- **`OperatorLevels`** holds both shapes (`IntLevels`, `PermissionSetLevels`). `PermissionSetLevels` follows the `paper` adapter's `NativePermissionLevels`, but Spigot has no `hasPermission(Permission, String)`. Operator levels and `/execute oplevel` caps read the source's own permission set and `Player.permissions()`, never CraftBukkit's Bukkit-aware set, so granting a Bukkit node cannot raise a level. The selector check calls CraftBukkit's `DelegatePermissionSet.hasPermission(boolean, String)`, the call its own selector branch makes, so it does not depend on which command CraftBukkit is checking. Non-player entities are level 0. Any unexpected shape fails closed at install with the usual rollback.
- **Spigot 1.20.5, 1.21 - 1.21.9, 26.1.x and 26.2** were not booted. They use name-table rows shared with booted versions.

## The legacy adapter

- **Interception.** `CommandListener` handles `PlayerCommandPreprocessEvent`. It acts only when the typed label (including `minecraft:<name>` and `commands.yml` aliases) resolves to CraftBukkit's `VanillaCommandWrapper`; other commands pass untouched. The wrappers are never replaced (see [Before Brigadier](#before-brigadier-188---1122)).
- **Grammars.** Ten hand-written grammars in `legacy/.../grammar` (`gamemode`, `time`, `weather`, `kill`, `effect`, `give`, `clear`, `tp`/`teleport`, `execute`, `scoreboard`) parse the typed arguments and yield the modern node names. `LegacyVersion` switches 1.8 - 1.12 differences inside a grammar. The node lists are in [Permissions](../permissions.md#188---1122-legacy-servers).
- **Selectors** are resolved with NMS `PlayerSelector` after `MinecraftServer.worldServer` is filled (`LegacyServer`), then checked by `core`'s `SelectorRules`. Anything it cannot resolve or parse is denied (fail closed).
- **Tab completion** is filtered by `TabCompleteFilter` through `TabCompleteEvent` on 1.9+; 1.8.8 has none.
- **Roots that differ:** `/tell` (and `/msg`, `/w`) is also denied when `minecraft.command.msg` is false, and `/xp` when `minecraft.command.experience` is false.
- **Other features** live in `legacy/.../feature` (`LegacyFeatures`): force-gamemode (the saved game mode is restored at join and kept at `/defaultgamemode`), operator blocks, and a Netty move-speed hook.
- **What the node count means.** `checkedNodeCount()` is the number of node paths the grammars can produce (135 on Spigot 1.8.8, 148 on 1.12.2), not nodes of a vanilla tree. It is only comparable between legacy versions.
- **Limits:** see [Compatibility](../compatibility.md#limits).

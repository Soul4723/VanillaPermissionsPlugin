# Live permission tests

Maintainer notes. `scripts/compat-smoke.sh` only proves the plugin installs its checks at startup. The live-test harness in `scripts/live-test/` proves the checks work for a real player: it boots a server in a throwaway directory, joins headless offline-mode bots, changes permissions from the console and checks what each bot is allowed to do.

## Boot smoke test

From the repository root, with Bash, curl, Python 3 and the appropriate JDK installed:

```bash
mvn verify
JAVA_BIN=/path/to/jdk21/bin/java bash scripts/compat-smoke.sh 1.21.11 target/VanillaPermissionsPlugin-*.jar --build 132
JAVA_BIN=/path/to/jdk25/bin/java bash scripts/compat-smoke.sh 26.3 target/VanillaPermissionsPlugin-*.jar --software purpur --build 2652
# Spigot: obtain BuildTools build 201 and verify the SHA-256 recorded in `.github/workflows/build.yml` first.
# Run BuildTools with JDK 17 for this revision:
/path/to/jdk17/bin/java -jar BuildTools.jar --rev 1.20.4 --compile spigot --output-dir "$HOME/spigot/1.20.4"
JAVA_BIN=/path/to/jdk17/bin/java bash scripts/compat-smoke.sh 1.20.4 target/VanillaPermissionsPlugin-*.jar \
  --software spigot --server-jar "$HOME/spigot/1.20.4/spigot-1.20.4.jar"
```

Run a clean build first if `target` contains jars from multiple versions; the glob must match one jar.

| Matrix server version | JDK |
| --- | --- |
| 1.8.8–1.16.5 | 8 |
| 1.17.1–1.20.4 | 17 |
| 1.20.5–1.21.11 | 21 |
| 26.x | 25 |

`JAVA_BIN` selects Java; `MIN_NODES` sets the minimum patched-node count (default 1000; use the matching `min_nodes` value in the boot matrix in `.github/workflows/build.yml` for older versions). `CACHE_DIR` defaults to `~/.cache/vpp-compat`, including per-software/version server caches. `WORK_DIR` defaults to a unique directory under `$CACHE_DIR/runs`; `KEEP_WORK=1` preserves it. `LOG_DIR` copies the final log out before cleanup. `JAVA_OPTS` adds JVM flags and `BOOT_TIMEOUT` changes the 240-second boot limit. The script chooses LuckPerms 5.4.158 for Java 8 and the current release from LuckPerms' metadata API for Java 11+.

## Running it

Needs Node 20+ and the Java version the server needs.

```bash
cd scripts/live-test
npm ci
node run.js \
  --server-jar ~/paper-1.21.11-132.jar \
  --java /usr/lib/jvm/temurin-21/bin/java \
  --plugin ../../target/VanillaPermissionsPlugin-*.jar \
  --luckperms ~/LuckPerms-Bukkit-5.5.87.jar \
  --scenario scenarios/gamemode.json --scenario scenarios/selectors.json
```

It prints one row per check and a `RESULT PASS|FAIL passed=… failed=… skipped=…` line, and exits 0 only if nothing failed and at least one check passed. `node run.js --help` lists every option. The useful ones:

| Option | Use |
| --- | --- |
| `--server-jar` | Paper, Spigot, Purpur or Pufferfish, 1.8.8 to 26.x. Linked into the server dir, never modified. |
| `--java` | Java binary for the server: 8 for 1.8.8–1.16.5, 16/17 for 1.17–1.20.4, 21 for 1.20.5–1.21.11, 25 for 26.x. LuckPerms 5.5.x requires Java 11+; use 5.4.158 on Java 8, as the boot smoke test does. |
| `--plugin` | Our jar. Omit it to drive a server without the plugin; scenarios marked `"when": {"plugin": true}` are then skipped. |
| `--luckperms` | Selects the `luckperms` backend (a compatible LuckPerms-Bukkit jar; 5.5.x needs Java 11+, while 5.4.158 runs on Java 8). The run fails at boot if LuckPerms does not enable. Without it the backend is `bukkit`: no permissions plugin, only ops and `permissions.yml`. |
| `--extra-plugin` | Any other plugin, e.g. ViaVersion and ViaBackwards (see [26.x](#minecraft-26x)). Repeatable. |
| `--client-version` | Protocol version the bot speaks. Default: the server's own. |
| `--time-scale` | Multiplies every wait. Use 3 or more on a shared or slow host; CI runners are fine at 1–2. |
| `--cache-dir` | Where Paper's patched jars and LuckPerms' libraries are kept between runs (default `~/.cache/vpp-live-test`). Without it every boot downloads the vanilla jar again. |
| `--work-dir` | Where server directories are created. Default `<cache-dir>/runs`: on disk, because `/tmp` is RAM-backed on many hosts and a server directory is a few hundred MB. Each boot's directory is deleted afterwards; if a check failed, its console log is kept next to it as `<dir>.console.log`. |
| `--keep` | Keep the server directories. |
| `--report` | Also write the rows as JSON. On GitHub Actions the table is added to the job summary too. |
| `--verbose` | Print console commands and every chat line the bots receive. |

Scenarios that need different server settings (the `server` block) each get their own boot; the rest share one. Before each scenario the players it lists are disconnected, de-opped and (with LuckPerms) cleared with `lp user <uuid> clear`.

## Scenarios

A scenario is a JSON file:

```json
{
  "name": "gamemode",
  "when": { "plugin": true, "backend": "luckperms" },
  "server": { "properties": { "max-players": 1 }, "files": { "permissions.yml": "..." } },
  "players": ["Bot"],
  "steps": [
    { "connect": "Bot" },
    { "perm": { "node": "minecraft.command.gamemode", "value": true } },
    { "name": "creative allowed", "command": "/gamemode creative", "expect": { "outcome": "allowed", "gamemode": "creative" } },
    { "tree": { "has": ["gamemode creative"], "lacks": ["time"] } }
  ]
}
```

Steps. Each can have `name` (shown in the table), `as` (bot name; default the first connected bot) and `when` (skip it on some runs).

| Step | Does |
| --- | --- |
| `{"connect": "Bot"}` | Joins and waits for the spawn teleport. With `"expect": {"refused": "<regex>"}` the join must be refused with a matching reason. |
| `{"disconnect": "Bot"}` | Leaves. |
| `{"op": "Bot"}`, `{"deop": "Bot"}` | Console `op`/`deop`, waits for the server's reply. |
| `{"perm": {"user", "node", "value"}}` | `lp user <uuid> permission set <node> <value>`; `"value": null` unsets. LuckPerms backend only. `user` defaults to the first bot. |
| `{"meta": {"user", "key", "value"}}` | `lp user <uuid> meta set <key> <value>`, e.g. selector limits. |
| `{"console": "<cmd>", "expect": "<regex>"}` | Any console command; waits for a matching line if `expect` is given. |
| `{"command": "/<cmd>", "expect": {...}}` | The bot runs the command. Checks below. |
| `{"tree": {"has": [...], "lacks": [...]}}` | Paths of literals in the command tree the server sent (1.13+; skipped before). Waits for the tree to update after a permission change. |
| `{"tab": {"text": "/gamemode c", "has": [...], "lacks": [...]}}` | Tab completion (works on every version; on 1.8–1.12 it is the only way to see command visibility). |
| `{"log": "<regex>", "absent": true?}` | A line in the server log since boot (or none). |

Command checks (`expect`), all optional:

- `outcome`: `denied` needs a refusal message; `allowed` needs none within the settle window. Refusals are recognised on every version: vanilla's `Unknown or incomplete command`/`Incorrect argument for command`, Bukkit's `Unknown command` and `I'm sorry, but you do not have permission…`, and this plugin's selector messages.
- `gamemode`: the bot's game mode after the command, tracked from the login, respawn and game-state packets.
- `chat` / `noChat`: a regex a received message must (not) match. Messages are flattened to English for common keys and to `key(args)` otherwise.
- `teleported`: whether the server teleported the bot.
- `settleMs` (default 1500). The command timeout is fixed at 3000 ms, scaled by `--time-scale`.

`when` (scenario or step): `backend`, `plugin` (true/false), `minVersion`, `maxVersion`, `software` and `not` (negates a condition object). Unmet conditions show as `SKIP`.

The scenarios in `scenarios/`:

| File | Checks |
| --- | --- |
| `scoreboard-wildcard.json` | Console scoreboard wildcard and empty `/execute unless entity` condition (1.13+). |
| `legacy-execute.json` | Slash and namespaced inner `/execute` commands respect creative denial (1.8.8–1.12.2; `@p` before 1.12). |
| `gamemode.json` | Per-mode nodes, the command tree and tab completion following them, `false` beating op. |
| `selectors.json` | `minecraft.selector`, self and player scope nodes, the limit meta, with two bots. |
| `execute.json` | `/execute if permission`, `feedback silent`, and the nodes on those branches. |
| `bypass.json` | Player limit, whitelist and forced game mode bypasses (own boot: `max-players=1`, `force-gamemode=true`). |
| `bukkit-permissions-yml.json` | No permissions plugin: a `permissions.yml` default-true node grants the gamemode root. Run with no `--luckperms`. |
| `vanilla-smoke.json` | Plugin-independent: op gates `/gamemode`, `/tp @a`. Proves the harness drives any version. |
| `aliases.json` | Redirect aliases (`/tp`, `/tell`, `/w`) gated by their own root node, falling back to the target's (own boot: writes `commands.yml`). |
| `folia.json` | Folia status, user and group permission changes refreshing online command trees, selector limits, and absence of `UnsupportedOperationException`. |

## Results

This page keeps no per-run pass counts: they go stale with every release. Boot and install is checked on every push and pull request by the boot matrix in `.github/workflows/build.yml`. Live bot results are written in the description of the pull request that ran them; the versions covered are summarised in [Compatibility](../compatibility.md#how-each-level-was-verified). Other projects do the same: spark, LuckPerms, Geyser and ViaVersion keep no per-run results in the repository.

Behaviour the harness accounts for:

- **Paper 1.21.3 and later** reject `@s` from a player without `minecraft.selector` during parsing, with vanilla's `Selector not allowed`. That happens before this plugin's `You do not have permission to use target selectors`, which is what 1.20.6 shows. Both count as denied.
- **`/execute if … run …`** prints nothing when the condition fails (only `/execute if …` without `run` prints `Test failed`).
- **Paper 26.x** writes `white-list=true` into a new `server.properties`; the harness sets `white-list=false`.
- **Flat worlds spawn slimes** even with `spawn-monsters=false`, and a dead bot gets `chat.disabled.options` for every command. The harness uses `difficulty=peaceful`.
- **Player data persists between scenarios** in one boot, so each scenario's first join resets the bot to survival.
- **Spigot 1.8–1.12** answer `deop` of a non-operator with `Could not de-op <name>`. **LuckPerms' first command** after boot can print an untranslated key (`luckperms.command.generic.clear`).
- **Skipped checks** are the version conditions in the scenarios: tab completion is only checked before 1.13, the command tree only from 1.13, `@s` from 1.12, and there is no force-gamemode bypass on Spigot 1.13+.
- CraftBukkit gates `/tp` by `minecraft.command.teleport` before the plugin's checks, so the `spigot` adapter gates redirect aliases by their own node or the target's, as Paper 1.20.6+ does; `aliases.json` covers it.

## Client and version coverage

The bot is [minecraft-protocol](https://github.com/PrismarineJS/node-minecraft-protocol) (PrismarineJS), pinned to an exact version in `package.json` with `package-lock.json`. Mineflayer is not used: we only need chat, commands, the command tree and a few state packets, and minecraft-protocol already handles login, configuration and keep-alives for every version.

minecraft-protocol 1.68.0 (`src/version.js`) supports 1.7, 1.8.8, 1.9.4, 1.10.2, 1.11.2, 1.12.2, 1.13.2, 1.14.4, 1.15.2, 1.16.5, 1.17.1, 1.18.2, 1.19–1.19.4, 1.20–1.20.6, 1.21.1, 1.21.3–1.21.6, 1.21.8, 1.21.9, 1.21.11 and 26.1. Releases in between that share a protocol number work too (1.21.10 is protocol 773, the same as 1.21.9). The harness pings the server first and picks the client version from the protocol number.

### Minecraft 26.x

minecraft-protocol has no 26.2 or 26.3 support yet. For those servers, install ViaVersion and ViaBackwards on the server and let the bot connect as 26.1:

```bash
node run.js --server-jar paper-26.3-206.jar --java temurin-25/bin/java \
  --extra-plugin ViaVersion-5.12.1.jar --extra-plugin ViaBackwards-5.12.1.jar \
  --client-version 26.1 ...
```

ViaVersion lets newer clients join an older server; ViaBackwards (on top of ViaVersion) lets older clients join a newer server, which is this case ([ViaVersion README, "Supported versions" table](https://github.com/ViaVersion/ViaVersion#readme)). Checked on Paper 26.3 build 206 with ViaVersion and ViaBackwards 5.12.1: ViaVersion logs `detected server version: 26.3 (777)` and the 26.1 bot (protocol 775) joins. ViaBackwards rewrites the command tree and chat for the older client, so a pass through Via proves the server side behaves, not that a native 26.3 client sees the identical packets. The Via plugins are not needed once minecraft-protocol supports 26.3.

## Prior art for the harness

- **PrismarineJS/node-minecraft-protocol** (`test/clientTest.js`): starts a real server per version with `online-mode=false`, a flat world, `generate-structures=false` and `use-native-transport=false`, and retries the status ping after the "Done" line because the server answers pings slightly later. The harness uses the same properties and `pingUntilReady`.
- **PrismarineJS/node-minecraft-wrap** (`lib/wrap_server.js`): the wrapper contract (write `eula.txt` and properties, wait for the Done regex, console on stdin, `stop`, delete the dir), implemented in `lib/server.js`.
- **PrismarineJS/mineflayer** (`test/externalTest.js`): ops the test bot from the console and waits for the server's confirmation before asserting, as the `op`, `deop`, `perm` and `meta` steps do.
- **lucko/LuckPerms** (`standalone/src/test/.../utils/CommandTester.java`): given-permissions / when-command / then-expect tests, the shape of the `perm` → `command` → `expect` steps. Its Docker tests are opt-in (`-PdockerTests`); likewise the live tests are separate from `mvn verify`.
- **itzg/docker-minecraft-server** (`tests/test.sh`, `mc-monitor status --retry-limit`): readiness by retrying a status ping, not by sleeping.

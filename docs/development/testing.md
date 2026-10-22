# Testing

There are three levels of tests:

- **Unit tests** run with `mvn verify`, locally and in CI.
- **Boot smoke tests** (`scripts/compat-smoke.sh`) start a real server with the plugin and LuckPerms and check that the plugin installed its checks. CI runs one for every supported server and version.
- **Live tests** (`scripts/live-test/`) join headless bot players to a real server, change their permissions and check what each one may do. They are run by hand; their results go in the description of the pull request that needed them.

Servers need the JDK of their Minecraft version:

| Minecraft | JDK |
| --- | --- |
| 1.8.8 - 1.16.5 | 8 |
| 1.17.1 - 1.20.4 | 17 |
| 1.20.5 - 1.21.11 | 21 |
| 26.x | 25 |

## Boot smoke test

Needs Bash, curl and Python 3. From the repository root, after `mvn verify` (with only one jar in `target`):

```bash
JAVA_BIN=/path/to/jdk21/bin/java bash scripts/compat-smoke.sh 1.21.11 target/VanillaPermissionsPlugin-*.jar --build 132
JAVA_BIN=/path/to/jdk25/bin/java bash scripts/compat-smoke.sh 26.3 target/VanillaPermissionsPlugin-*.jar --software purpur --build 2652
```

`--software` is `paper` (the default), `folia`, `purpur` or `pufferfish`, and `--build` pins a build; the pinned builds are the rows of the boot matrix in `.github/workflows/build.yml`. Without `--build`, Paper and Folia use the newest stable build and Purpur the newest build. Pufferfish publishes no checksums, so only the builds pinned in the script can be used.

Spigot cannot be downloaded. Build it with the BuildTools build pinned in `build.yml` (`BUILDTOOLS_BUILD`, checked against `BUILDTOOLS_SHA256`) and pass the jar:

```bash
/path/to/jdk17/bin/java -jar BuildTools.jar --rev 1.20.4 --compile spigot --output-dir ~/spigot/1.20.4
JAVA_BIN=/path/to/jdk17/bin/java bash scripts/compat-smoke.sh 1.20.4 target/VanillaPermissionsPlugin-*.jar \
  --software spigot --server-jar ~/spigot/1.20.4/spigot-1.20.4.jar
```

The last line is `RESULT ... status=PASS|FAIL nodes=<n> ...`, and the exit code follows it. Environment variables:

| Variable | Use |
| --- | --- |
| `JAVA_BIN` | Java for the server (default `java`). LuckPerms is the current release on Java 11+, and 5.4.158 on Java 8. |
| `MIN_NODES` | Fewest patched command nodes that count as a pass (default 1000). Old versions have fewer; use the row's `min_nodes` from `build.yml`. |
| `BOOT_TIMEOUT` | Seconds to wait for startup (default 240). |
| `JAVA_OPTS` | Extra JVM flags. |
| `CACHE_DIR` | Downloads and per-version server caches (default `~/.cache/vpp-compat`). |
| `WORK_DIR`, `KEEP_WORK=1` | Server directory (default: a new directory under `$CACHE_DIR/runs`), and keep it afterwards. |
| `LOG_DIR` | Copy the server log here. |

## Live tests

Needs Node 22 or later.

```bash
cd scripts/live-test
npm ci
node run.js \
  --server-jar ~/paper-1.21.11-132.jar \
  --java /path/to/jdk21/bin/java \
  --plugin ../../target/VanillaPermissionsPlugin-*.jar \
  --luckperms ~/LuckPerms-Bukkit-5.5.87.jar \
  --scenario scenarios/gamemode.json --scenario scenarios/selectors.json
```

It prints one row per check and `RESULT PASS|FAIL passed=… failed=… skipped=…`, and exits 0 only if nothing failed and at least one check passed. `node run.js --help` lists every option:

| Option | Use |
| --- | --- |
| `--server-jar` | Paper, Spigot, Folia, Purpur or Pufferfish, 1.8.8 to 26.x. Linked into the server directory, never modified. |
| `--java` | Java for the server (see the table above). |
| `--plugin` | The plugin jar. Without it, scenarios marked `"when": {"plugin": true}` are skipped. |
| `--luckperms` | A LuckPerms-Bukkit jar; selects the `luckperms` backend. 5.5.x needs Java 11+; use 5.4.158 on Java 8. Without it the backend is `bukkit`: operators and `permissions.yml` only. |
| `--extra-plugin` | Any other plugin, such as ViaVersion (see [Minecraft 26.x](#minecraft-26x)). Repeatable. |
| `--client-version` | Protocol version the bots speak. Default: the server's. |
| `--time-scale` | Multiply every wait; use 3 or more on a slow or busy machine. |
| `--cache-dir` | Keeps the vanilla jar, libraries and LuckPerms' libraries between runs (default `~/.cache/vpp-live-test`). |
| `--work-dir`, `--keep` | Where server directories are created (default `<cache-dir>/runs`), and keep them afterwards. Otherwise each is deleted after its run, keeping `<dir>.console.log` if a check failed. |
| `--report` | Also write the results as JSON. |
| `--verbose` | Print console commands and every message the bots receive. |

Scenarios with a `server` block get a server of their own; the others share one. Before each scenario, its players are disconnected, de-opped and, with LuckPerms, cleared.

### Scenarios

A scenario is a JSON file in `scenarios/`; its `description` says what it checks. `vanilla-smoke.json` needs neither the plugin nor LuckPerms, so it is a quick way to see that the harness works with a server.

```json
{
  "name": "gamemode",
  "description": "Per-mode gamemode nodes.",
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

Every step may have `name` (shown in the results), `as` (which bot; default the first connected) and `when` (skip it on some servers).

| Step | Does |
| --- | --- |
| `{"connect": "Bot"}` | Joins and waits for the spawn teleport. With `"expect": {"refused": "<regex>"}` the join must be refused with a matching reason. |
| `{"disconnect": "Bot"}` | Leaves. |
| `{"op": "Bot"}`, `{"deop": "Bot"}` | Console `op` or `deop`, waiting for the reply. |
| `{"perm": {"user", "node", "value"}}` | Sets a LuckPerms node on the player; `"value": null` unsets it. `user` defaults to the first bot. |
| `{"meta": {"user", "key", "value"}}` | Sets LuckPerms meta, such as a selector limit. |
| `{"console": "<cmd>", "expect": "<regex>"}` | Any console command, optionally waiting for a matching line. |
| `{"command": "/<cmd>", "expect": {...}}` | The bot runs the command; checks below. |
| `{"tree": {"has": [...], "lacks": [...]}}` | Literal paths in the command tree the bot received (1.13+). Waits for the tree to update after a permission change. |
| `{"tab": {"text": "/gamemode c", "has": [...], "lacks": [...]}}` | Tab completion. Before 1.13 this is the only way to see which commands a player is offered. |
| `{"log": "<regex>", "absent": true}` | A line in the server log since boot, or with `absent`, none. |

Command checks in `expect`, all optional:

- `outcome`: `denied` needs a refusal message, `allowed` needs none within the settle time. Vanilla's, Bukkit's, Paper's and this plugin's refusal messages are all recognised.
- `gamemode`: the bot's game mode afterwards.
- `chat` / `noChat`: a regex that some message must match, or that no message may match. Common translation keys are rendered in English; others appear as `key(args)`.
- `teleported`: whether the server teleported the bot.
- `settleMs`: how long to watch for messages (default 1500 ms).

`when` takes `backend`, `plugin` (true or false), `minVersion`, `maxVersion`, `software` (`paper`, `spigot` or `folia`) and `not` (a condition that must not hold). Skipped checks show as `SKIP`.

Differences between servers that scenarios have to allow for:

- Paper 1.21.3 and later refuse `@s` without `minecraft.selector` while parsing, with vanilla's `Selector not allowed`, before the plugin's own message. Both count as denied.
- `/execute if … run …` prints nothing when the condition fails.
- Spigot 1.8 - 1.12 answer `deop` of a non-operator with `Could not de-op <name>`.
- Player data survives between scenarios on one server, so each scenario starts its bots in survival mode.

### Minecraft 26.x

The bots use [minecraft-protocol](https://github.com/PrismarineJS/node-minecraft-protocol), pinned in `package.json`. It does not speak 26.2 or 26.3 yet. For those servers, install ViaVersion and ViaBackwards with `--extra-plugin` and let the bots connect as 26.1:

```bash
node run.js --server-jar paper-26.3-206.jar --java /path/to/jdk25/bin/java \
  --extra-plugin ViaVersion-5.12.1.jar --extra-plugin ViaBackwards-5.12.1.jar \
  --client-version 26.1 ...
```

ViaBackwards rewrites the command tree and messages for the older client, so such a run shows that the server behaves correctly, not that a 26.3 client receives identical packets.

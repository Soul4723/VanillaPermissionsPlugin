# VanillaPermissionsPlugin

A drop-in Paper port of [DrexHD's VanillaPermissions](https://github.com/DrexHD/VanillaPermissions). It lets LuckPerms decide, node by node, who may use each part of every vanilla command (`/gamemode creative`, `/time set`, `/tp <player>`…), whom they may target with names and `@` selectors, and a few vanilla operator features.

Paper still parses and runs the commands itself. The plugin only adds checks to the existing vanilla command tree, and only vanilla commands are affected (see [Plugin commands](#plugin-commands-from-other-plugins)).

## Requirements and installation

- Paper **1.20.6** (the only tested version) on Java 21
- [LuckPerms](https://luckperms.net/) 5.5 or newer

1. Put `VanillaPermissionsPlugin-<version>.jar` in `plugins/`. No other plugin (CommandAPI is not needed), Java agent or startup flag is required.
2. Restart the server. The log should show both of these lines:
   - `Installed vanilla /execute permission, feedback, and oplevel extensions`
   - `Added LuckPerms checks to <n> vanilla command nodes`
3. Run `/vanillapermissions debug` to check the plugin and server version, the LuckPerms connection and the number of checked nodes.

If the plugin cannot patch the command tree (another Paper version, or something removed a vanilla command), it logs why, restores the vanilla commands and disables itself on the next tick. The server keeps running. If LuckPerms is missing, Paper does not load the plugin and logs the missing dependency. If LuckPerms is installed but failed to start, the plugin logs that and disables itself.

## How a node is resolved

Every check asks LuckPerms for the node in the player's current context. What the result means depends on the node family:

| Node family | `true` | `false` | unset |
| --- | --- | --- | --- |
| Command nodes `minecraft.command.…` | Allowed, even for non-operators. | Denied, even for operators. | Vanilla behaviour (see below). |
| Selector syntax `minecraft.selector` | `@` selectors allowed. | `@` selectors denied. | Vanilla behaviour: operators only (or Paper's `minecraft.command.selector`). |
| Selector scope `minecraft.selector.<self\|player\|entity>.…` | Allowed. | Denied. | Allowed. |
| Selector meta `minecraft.selector.<limit\|weight>.…` | Not used: these are integer meta values (`/lp … meta set`). | Not used. | No limit, or no weight check. |
| Bypass `minecraft.bypass.…` | Bypass. | No bypass from this plugin. | No bypass from this plugin. |
| Operator blocks `minecraft.operator_block.…` and debug stick `minecraft.debug_stick.use.…` | Vanilla/Paper behaviour (cannot give access). | Access taken away. | Vanilla/Paper behaviour. |

Rules that apply everywhere:

- **Unset command roots follow vanilla:** operators (level 2 or higher) only. Open to everyone on Paper by default: `/minecraft:help` (plain `/help` is Bukkit's), `/me`, `/msg` (`/tell`, `/w`), `/teammsg` (`/tm`) and `/trigger`. Our node names are the same strings as Paper's own Bukkit command permissions, so a value set in LuckPerms is also what Paper sees.
- **Parent and child nodes are checked independently along the command path.** The player needs the root and every node on the path to be allowed. An unset sub-node follows vanilla, which usually means "open to anyone who can use the command". A few sub-commands keep their own vanilla operator requirement (for example `/random reset`); `/lp verbose` shows them. **Granting a root therefore exposes all of its sub-commands**; set the branches you don't want to `false`.
- **Wildcards** work when LuckPerms `apply-wildcards` is on (the default). `minecraft.command.time.*` matches every node below `minecraft.command.time`, but not `minecraft.command.time` itself. A wildcard also covers sub-nodes, so `minecraft.command.* false` plus `minecraft.command.time true` still denies `/time query`; add `minecraft.command.time.* true` to allow the sub-commands. Wildcards do not apply to meta keys.
- **Console and command blocks** are never affected by command or selector nodes.
- **Bypass nodes are grant-only.** With `false` or unset, vanilla behaviour applies: operators can still join while not whitelisted, and operators marked `bypassesPlayerLimit` in `ops.json` can still join a full server.
- **To see every node a command checks,** run `/lp verbose on <player>`, have the player run or tab-complete the command, then `/lp verbose off`.

## Permission reference

LuckPerms syntax used below: `/lp group <group> permission set <node> true|false` and `/lp group <group> meta set <key> <value>`.

### Commands

| Node | Controls |
| --- | --- |
| `minecraft.command.<command>` | The command itself, e.g. `minecraft.command.time`. |
| `minecraft.command.<command>.<node>…` | Each sub-command and argument, named as in the vanilla command tree, e.g. `minecraft.command.time.set`, `minecraft.command.time.set.time`, `minecraft.command.teleport.targets.destination`. |
| `minecraft.command.gamemode.<survival\|creative\|adventure\|spectator>` | One game mode. |
| `minecraft.command.gamemode.<mode>.target` | Setting that mode for another player (`/gamemode <mode> <player>`). |

- **Gamemode:** the plugin replaces vanilla's single mode argument with one literal per mode, which is why the nodes above name the mode.
- **`minecraft:<command>` aliases** (for example `/minecraft:gamemode`) use the same nodes as the command.
- **Redirect aliases** (`/tp` → `teleport`, `/tell` and `/w` → `msg`, `/tm` → `teammsg`, `/xp` → `experience`) have their own root node (`minecraft.command.tp`), while their arguments use the target command's nodes (`minecraft.command.teleport.targets…`).

### `/execute` additions

Upstream's `/execute` extensions are added and use normal command nodes:

| Syntax | Node | Effect |
| --- | --- | --- |
| `/execute if\|unless permission <entity> <node>` | `minecraft.command.execute.<if\|unless>.permission…` | Condition on whether the entity has a LuckPerms node (unset counts as not having it). |
| `/execute feedback silent\|console` | `minecraft.command.execute.feedback.<silent\|console>` | Hide command output, or send it to the console. |
| `/execute feedback entity <targets>` | `minecraft.command.execute.feedback.entity.targets` | Redirect vanilla command output to other entities. Permission and selector checks stay with the original player, and plugin commands still reply to the original player. |
| `/execute oplevel <0-4>`, `oplevel entity <targets>` | `minecraft.command.execute.oplevel…` | Lower the vanilla operator level the rest of the command runs with, never above the caller's. Command and selector nodes are still checked for the caller. |

### Selectors

| Node | Type | Controls |
| --- | --- | --- |
| `minecraft.selector` | node | Typing `@` selectors at all. |
| `minecraft.selector.self.<path>` | node | Targeting yourself. |
| `minecraft.selector.player.<path>` | node | Targeting other players. |
| `minecraft.selector.entity.<path>` | node | Targeting non-player entities. |
| `minecraft.selector.limit.<path>` | integer meta | Maximum number of targets. |
| `minecraft.selector.weight.<path>` | integer meta | You cannot target players whose weight for the same path is higher than yours. Only checked if you have a weight, and only for targets whose LuckPerms data is loaded (normally online players). |

- `<path>` is the command name, then the argument name, then every other node of the command as typed, **including optional arguments**:
  - `/gamemode survival @s` → `gamemode.target.survival.target`
  - `/effect give @s speed` → `effect.targets.give.targets.effect`
  - `/effect give @s speed 5` → `effect.targets.give.targets.effect.seconds`
- Because the path changes with optional arguments, use wildcards for scope nodes (`minecraft.selector.player.effect.targets.*`) and set limit and weight meta for each form (meta has no wildcards).
- Scope, limit and weight apply to every player or entity argument, whether a name or a selector was typed. `minecraft.selector` only controls `@` syntax.
- Fake scoreboard names are not players or entities; only the limit applies to them.
- The whole selection is checked before the command runs, so a partly denied `@a` changes nothing.

### Bypass nodes

Grant-only (see the table above).

| Node | Lets the player… |
| --- | --- |
| `minecraft.bypass.whitelist` | Join while not whitelisted. |
| `minecraft.bypass.player-limit` | Join when the server is full. |
| `minecraft.bypass.force-gamemode` | Keep their game mode when the server forces the default game mode. |
| `minecraft.bypass.move-speed.player` | Skip the "moved too quickly" and "moved wrongly" checks. |

### Operator blocks and debug stick

Deny-only: `false` takes access away; `true` and unset leave vanilla/Paper in charge.

| Node | Controls |
| --- | --- |
| `minecraft.operator_block.<command_block\|structure_block\|jigsaw>.<place\|view\|break>` | Placing, opening and breaking the block. `command_block` also covers repeating and chain command blocks. |
| `minecraft.debug_stick.use.<namespace>.<block>` | Using the debug stick on one block: the block id with `:` replaced by `.`, e.g. `minecraft.debug_stick.use.minecraft.oak_trapdoor`. `minecraft.debug_stick.use.*` covers every block. Applies to left and right clicks and to both hands. |

`view` means opening the block's screen. It is only checked when a creative player right-clicks with the main hand and is not sneaking with an item, because that is the only click vanilla opens the screen for. Upstream also has `edit`; Paper has no API for it, so deny `view` instead.

**How to grant these.** Use Paper's own nodes (registered by Paper, settable in LuckPerms). Our deny nodes still apply to players who got access this way.

| Paper's own node | Default | Allows |
| --- | --- | --- |
| `minecraft.commandblock` | operator | A non-operator in creative mode to open, edit and break command blocks. Placing still needs operator. |
| `minecraft.debugstick` | operator | The debug stick in creative mode. |
| `minecraft.debugstick.always` | nobody | The debug stick in any game mode. |

Structure blocks and jigsaws are operator and creative only, with no Paper node.

Paper's own `minecraft.admin.command_feedback` (operators have it by default) controls who sees other players' "[Name: did X]" command feedback. It replaces upstream's `minecraft.adminbroadcast.receive`, which this plugin does not add.

### Plugin nodes

| Node | Default | Controls |
| --- | --- | --- |
| `vanillapermissions.use` | operator (`op`) | `/vanillapermissions` help. |
| `vanillapermissions.admin` | operator (`op`) | `/vanillapermissions check` and `debug`. Includes `vanillapermissions.use`. |

## Recipes

Self-only survival mode for a non-operator group:

```text
/lp group member permission set minecraft.command.gamemode true
/lp group member permission set minecraft.command.gamemode.creative false
/lp group member permission set minecraft.command.gamemode.adventure false
/lp group member permission set minecraft.command.gamemode.spectator false
/lp group member permission set minecraft.selector true
/lp group member permission set minecraft.selector.player.gamemode.target.survival.target false
```

`/gamemode survival` and `/gamemode survival @s` work; `/gamemode survival Steve` and `@a` are refused.

`/tp` to coordinates only, not to or of other players:

```text
/lp group member permission set minecraft.command.tp true
/lp group member permission set minecraft.command.teleport.destination false
/lp group member permission set minecraft.command.teleport.targets false
```

Moderators can apply `/effect` to other players, at most five at a time:

```text
/lp group moderator permission set minecraft.command.effect true
/lp group moderator permission set minecraft.selector true
/lp group moderator meta set minecraft.selector.limit.effect.targets.give.targets.effect 5
/lp group moderator meta set minecraft.selector.limit.effect.targets.give.targets.effect.seconds 5
/lp group moderator meta set minecraft.selector.limit.effect.targets.give.targets.effect.seconds.amplifier 5
/lp group moderator meta set minecraft.selector.limit.effect.targets.give.targets.effect.seconds.amplifier.hideParticles 5
/lp group moderator meta set minecraft.selector.limit.effect.targets.give.targets.effect.infinite 5
/lp group moderator meta set minecraft.selector.limit.effect.targets.give.targets.effect.infinite.amplifier 5
/lp group moderator meta set minecraft.selector.limit.effect.targets.give.targets.effect.infinite.amplifier.hideParticles 5
```

Meta has no wildcards, so every way of typing `/effect give` needs its own line: with or without `<seconds>` or `infinite`, `<amplifier>` and `<hideParticles>`. A form without a line has no limit.

Builders can use the debug stick in creative mode, except on oak trapdoors:

```text
/lp group builder permission set minecraft.debugstick true
/lp group builder permission set minecraft.debug_stick.use.minecraft.oak_trapdoor false
```

Take operator blocks away from an operator group:

```text
/lp group admin permission set minecraft.operator_block.* false
```

Deny every vanilla command except `/time` (plugin commands are not affected):

```text
/lp group member permission set minecraft.command.* false
/lp group member permission set minecraft.command.time true
/lp group member permission set minecraft.command.time.* true
```

`minecraft.command.time.*` does not match the root `minecraft.command.time`, so grant the root separately. The `.*` grant is needed because `minecraft.command.* false` also covers every sub-command of `/time`. See the wildcard rule in [How a node is resolved](#how-a-node-is-resolved). `minecraft.command.* false` also matches Paper's own `minecraft.command.selector`, so set `minecraft.selector true` if the group should keep `@` selectors.

## What players see when denied

- **Command nodes:** denied commands and branches disappear from the client's command list and tab completion. Typing one gives vanilla's "Unknown or incomplete command". The plugin re-sends the command list about half a second after LuckPerms user data, group data or contexts change. This overlaps with LuckPerms' own `update-client-command-list` option (default true); it works either way.
- **Selector checks:** the command fails with a red message, one of:
  - `You do not have permission to use target selectors`
  - `You can select at most N targets here`
  - `You do not have permission to target non-player entities here`
  - `You do not have permission to target other players here`
  - `You do not have permission to target yourself here`
  - `You do not have permission to target players with a higher selector weight than yours`
  - `This target cannot be checked against selector permissions, so it is not allowed`
  - `The selected targets could not be checked against selector permissions`
- **Operator blocks and debug stick:** the action is cancelled with a red chat message, for example `You do not have permission to open command blocks.` or `You do not have permission to use the debug stick on this block.`

## Plugin commands from other plugins

Only vanilla commands get `minecraft.command.*` checks. Bukkit and plugin commands (`/lp`, Bukkit's `/help`, `/version`, EssentialsX…) keep their own permissions, even under `minecraft.command.*`.

If a plugin takes over a vanilla name (for example EssentialsX `/gamemode` or `/time`), the plugin's version uses the plugin's permissions. The vanilla version stays reachable as `/minecraft:gamemode` with the normal `minecraft.command.gamemode…` nodes.

## The `/vanillapermissions` command

- `/vanillapermissions check <player> [node]` shows how LuckPerms resolves a node for an online player in their current context: `true`, `false`, `unset (vanilla behaviour applies)`, or for limit and weight nodes the meta value or `unset (no limit)` / `unset (no weight check)`. Without a node it shows a summary of a few nodes. It reports the raw LuckPerms value, not the final outcome: an unset root still lets operators through.
- `/vanillapermissions debug` shows the plugin and server version (tested or not), the LuckPerms connection and version, and how many vanilla command nodes carry checks.

## Limitations

- Only Paper 1.20.6 is tested. On another version the plugin logs a warning, and disables itself if the command tree cannot be patched.
- Operator block and debug stick nodes can only take access away.
- Upstream nodes this plugin does not add, because Paper has no API for them: `minecraft.bypass.spawn-protection`, `minecraft.bypass.chat-speed`, `minecraft.bypass.move-speed.vehicle.<entity>`, `minecraft.debug_chart`, `minecraft.<query|load>.<entity|block>`, `minecraft.adminbroadcast.receive` (use Paper's `minecraft.admin.command_feedback`) and the operator block `edit` action.
- Selector weight is only compared for targets whose LuckPerms data is loaded (normally online players).
- With `/execute feedback console` the console may show a result twice (the direct output plus the admin log line).

## Troubleshooting

- **I granted a command but the player still can't use part of it.** Run `/lp verbose on <player>` and look for a `false` on a child node, or one inherited from a group. `@` selectors also need `minecraft.selector`, and the selector scope nodes must not be `false`.
- **The player can do more than I wanted.** Granting a root grants every sub-command and target. Set the unwanted branches or selector scopes to `false`.
- **An operator lost access.** An explicit `false` beats operator status. Look for the node in the operator's own and inherited groups, including wildcards.
- **The plugin disabled itself at startup.** The log says why. Usually the server is not Paper 1.20.6, or LuckPerms failed to start.
- **`/minecraft:<command>` behaves differently from `/<command>`.** Another plugin owns the plain name. See [Plugin commands from other plugins](#plugin-commands-from-other-plugins).

## Building from source

```text
mvn verify
```

Requires JDK 21 and Maven. The plugin jar is written to `target/`; the unit tests run as part of the build.

## License

MIT. See [LICENSE](LICENSE).

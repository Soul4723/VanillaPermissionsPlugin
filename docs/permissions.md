# Permissions

How a node resolves, and every node the plugin checks. LuckPerms syntax is used for examples: `/lp group <group> permission set <node> true|false` and `/lp group <group> meta set <key> <value>`. For other plugins see [Permission plugins](providers.md).

## How a node is resolved

Every check asks the permissions plugin for the node in the player's current context (with LuckPerms; other plugins answer through Bukkit, where a node is unset unless the plugin or a registered default sets it). What the result means depends on the node family:

| Node family | `true` | `false` | unset |
| --- | --- | --- | --- |
| Command nodes `minecraft.command.…` | Allowed, even for non-operators. | Denied, even for operators. | Vanilla behaviour (see below). |
| Selector syntax `minecraft.selector` | `@` selectors allowed. | `@` selectors denied. | Vanilla behaviour: operators only (or Paper's `minecraft.command.selector`). |
| Selector scope `minecraft.selector.<self\|player\|entity>.…` | Allowed. | Denied. | Allowed. |
| Selector meta `minecraft.selector.<limit\|weight>.…` | Not used: these are integer meta values (`/lp … meta set`). | Not used. | No limit, or no weight check. |
| Bypass `minecraft.bypass.…` | Bypass. | No bypass from this plugin. | No bypass from this plugin, even for operators (vanilla behaviour). |
| Operator blocks `minecraft.operator_block.…` and debug stick `minecraft.debug_stick.use.…` | Vanilla/Paper behaviour (cannot give access). | Access taken away. | Vanilla/Paper behaviour. |

Rules that apply everywhere:

- **Unset command roots follow vanilla:** operators (level 2 or higher) only. Open to everyone on Paper by default: `/minecraft:help` (plain `/help` is Bukkit's), `/me`, `/msg` (`/tell`, `/w`), `/teammsg` (`/tm`) and `/trigger`. Our node names are the same strings as Paper's own Bukkit command permissions, so a value set in your permissions plugin is also what Paper sees.
- **Parent and child nodes are checked independently along the command path.** The player needs the root and every node on the path to be allowed. An unset sub-node follows vanilla, which usually means "open to anyone who can use the command". A few sub-commands keep their own vanilla operator requirement (for example `/random reset`); `/lp verbose` shows them. **Granting a root therefore exposes all of its sub-commands**; set the branches you don't want to `false`.
- **Console and command blocks** are never affected by command or selector nodes.
- **To see every node a command checks,** run `/lp verbose on <player>`, have the player run or tab-complete the command, then `/lp verbose off`.

## Wildcards

Wildcards work when LuckPerms `apply-wildcards` is on (the default).

- `minecraft.command.time.*` matches every node below `minecraft.command.time`, but not `minecraft.command.time` itself.
- A wildcard also covers sub-nodes, so `minecraft.command.* false` plus `minecraft.command.time true` still denies `/time query`. Add `minecraft.command.time.* true` to allow the sub-commands.
- `minecraft.command.* false` also matches Paper's own `minecraft.command.selector`, so set `minecraft.selector true` if the group should keep `@` selectors.
- Wildcards do not apply to meta keys.

## Operator defaults

- **Operators have every other node that is not explicitly `false`.** With LuckPerms' default `apply-bukkit-default-permissions: true`, a node no plugin registers defaults to "operators", so LuckPerms answers `true` for operators and unset for everyone else. Nodes Paper registers use Paper's default instead: most command roots (`minecraft.command.time`) are `false` for non-operators, a few (`minecraft.command.me`) are `true` for everyone. For command nodes this gives the same result as vanilla. Through other permissions plugins, nodes no plugin registers are unset for operators instead, which also gives the vanilla result.
- **The four bypass nodes are declared with default `false`,** so unset means vanilla behaviour for everyone, operators included. In vanilla, operators pass the whitelist anyway, only operators with `bypassesPlayerLimit` in `ops.json` pass the player limit, and nobody skips forced game mode or the movement check. Set a node to `true` to grant that bypass.
- **Bypass nodes are grant-only.** `false` and unset both leave vanilla behaviour: not whitelisted means not joining, and a full server is full.
- **`minecraft.selector` is declared with default `op`** (vanilla: selectors are for operators). It has the child `minecraft.command.selector`, which Paper checks while parsing a selector on 1.21.3 and later, so granting `minecraft.selector` is enough on every supported version.
- **Plugin nodes** `vanillapermissions.use` and `vanillapermissions.admin` default to `op`; see [Commands](commands.md).

## Node reference

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
- Only vanilla commands get these checks; see [Plugin commands from other plugins](troubleshooting.md#plugin-commands-from-other-plugins).

### `/execute` additions

Upstream's `/execute` extensions are added and use normal command nodes:

| Syntax | Node | Effect |
| --- | --- | --- |
| `/execute if\|unless permission <entity> <node>` | `minecraft.command.execute.<if\|unless>.permission…` | Condition on whether the entity has a node (unset counts as not having it). |
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
  - Aliases use the full command's path: `/minecraft:gamemode creative Bob` → `gamemode.target.creative.target`, `/tp Bob` → `teleport.destination.destination`.
- Because the path changes with optional arguments, use wildcards for scope nodes (`minecraft.selector.player.effect.targets.*`) and set limit and weight meta for each form (meta has no wildcards).
- Scope, limit and weight apply to every player or entity argument, whether a name or a selector was typed. `minecraft.selector` only controls `@` syntax.
- Fake scoreboard names are not players or entities; only the limit applies to them.
- The whole selection is checked before the command runs, so a partly denied `@a` changes nothing.
- Limit and weight are read from LuckPerms meta, or from Vault chat info with other plugins; see [Permission plugins](providers.md).

### Bypass nodes

Grant-only (see the table above). The whitelist and player-limit bypasses are checked while the player logs in. LuckPerms has loaded the player's data by then. Some other permissions plugins attach permissions only once the player has joined (GroupManager does), so for them the plugin also asks Vault's permission service, which reads the stored data; without Vault, those two bypasses need a plugin that attaches permissions at login.

| Node | Default | Lets the player… |
| --- | --- | --- |
| `minecraft.bypass.whitelist` | false | Join while not whitelisted. |
| `minecraft.bypass.player-limit` | false | Join when the server is full. |
| `minecraft.bypass.force-gamemode` | false | Keep their game mode when the server forces the default game mode. |
| `minecraft.bypass.move-speed.player` | false | Skip the "moved too quickly" and "moved wrongly" checks. |

### Operator blocks and debug stick

Deny-only: `false` takes access away; `true` and unset leave vanilla/Paper in charge.

| Node | Controls |
| --- | --- |
| `minecraft.operator_block.<command_block\|structure_block\|jigsaw>.<place\|view\|break>` | Placing, opening and breaking the block. `command_block` also covers repeating and chain command blocks. |
| `minecraft.debug_stick.use.<namespace>.<block>` | Using the debug stick on one block: the block id with `:` replaced by `.`, e.g. `minecraft.debug_stick.use.minecraft.oak_trapdoor`. `minecraft.debug_stick.use.*` covers every block. Applies to left and right clicks and to both hands. |

`view` means opening the block's screen. It is only checked when a creative player right-clicks with the main hand and is not sneaking with an item, because that is the only click vanilla opens the screen for. Upstream also has `edit`; Paper has no API for it, so deny `view` instead.

**How to grant these.** Use Paper's own nodes (registered by Paper, settable in your permissions plugin). The deny nodes above still apply to players who got access this way.

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

## What players see when denied

- **Command nodes:** denied commands and branches disappear from the client's command list and tab completion. Typing one gives vanilla's "Unknown or incomplete command". How quickly the list updates after a permission change depends on the permissions plugin; see [Permission plugins](providers.md).
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

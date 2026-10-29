# Permission nodes

Every node the plugin checks. Examples use LuckPerms; for other plugins see [Permissions plugins](providers.md). Read [How permissions work](how-it-works.md) first if you have not.

## Commands

| Node | Controls |
| --- | --- |
| `minecraft.command.<command>` | The command itself, e.g. `minecraft.command.time`. |
| `minecraft.command.<command>.<node>…` | Each sub-command and argument, e.g. `minecraft.command.time.set`, `minecraft.command.time.set.time`, `minecraft.command.teleport.targets.destination`. |
| `minecraft.command.gamemode.<survival\|creative\|adventure\|spectator>` | One game mode. |
| `minecraft.command.gamemode.<mode>.target` | Setting that mode for another player. |

Use `/lp verbose` or `/vanillapermissions check` to find the node for a command; see [Finding the node for a command](how-it-works.md#finding-the-node-for-a-command). Only vanilla commands get these nodes; see [Troubleshooting](troubleshooting.md#a-command-ignores-my-nodes). On 1.8.8 - 1.12.2, only ten commands have sub-command nodes; see [Compatibility](compatibility.md#188---1122).

## Aliases

`/minecraft:<command>` uses the same nodes as the command.

`/tp` (teleport), `/tell` and `/w` (msg), `/tm` (teammsg) and `/xp` (experience) have their own root node, such as `minecraft.command.tp`. Their arguments use the nodes of the command they point to, such as `minecraft.command.teleport.targets…`.

If the alias node is unset, the root of the target command decides, so granting `minecraft.command.teleport` opens `/tp` too. On 1.8.8 - 1.12.2, `/tp` needs `minecraft.command.tp`; see [Compatibility](compatibility.md#188---1122).

On Spigot 1.19 and later and Paper 1.19 - 1.20.5, a `/tell`, `/w` or `/tm` typed by a player ignores `minecraft.command.msg` or `minecraft.command.teammsg` set to `false` while its own node is unset. Set the alias node to `false` too.

## `/execute` additions

The plugin adds the original Fabric mod's `/execute` extensions. They use normal command nodes.

| Syntax | Node | Effect |
| --- | --- | --- |
| `/execute if\|unless permission <entity> <node>` | `minecraft.command.execute.<if\|unless>.permission…` | Tests whether the entity has a node. Unset counts as not having it. |
| `/execute feedback silent\|console` | `minecraft.command.execute.feedback.<silent\|console>` | Hides command output, or sends it to the console. |
| `/execute feedback entity <targets>` | `minecraft.command.execute.feedback.entity.targets` | Sends vanilla command output to other entities. Plugin commands still reply to the original player. |
| `/execute oplevel <0-4>`, `oplevel entity <targets>` | `minecraft.command.execute.oplevel…` | Lowers the operator level the rest of the command runs with. |

Feedback entity and oplevel never raise the caller's permissions. Command and selector nodes are still checked for the caller.

## Selectors

| Node | Type | Controls |
| --- | --- | --- |
| `minecraft.selector` | node | Typing `@` selectors at all. |
| `minecraft.selector.self.<path>` | node | Targeting yourself. |
| `minecraft.selector.player.<path>` | node | Targeting other players. |
| `minecraft.selector.entity.<path>` | node | Targeting non-player entities. |
| `minecraft.selector.limit.<path>` | integer meta | Maximum number of targets. See [Selector limits and weights](#selector-limits-and-weights). |
| `minecraft.selector.weight.<path>` | integer meta | Which players you may target. See [Selector limits and weights](#selector-limits-and-weights). |

`<path>` is the command name, then the argument name, then the rest of the command as typed, including optional arguments:

- `/gamemode survival @s` is `gamemode.target.survival.target`
- `/effect give @s speed` is `effect.targets.give.targets.effect`
- `/effect give @s speed 5` is `effect.targets.give.targets.effect.seconds`

Aliases use the path of the full command. `/minecraft:gamemode creative Bob` is `gamemode.target.creative.target` and `/tp Bob` is `teleport.destination.destination`.

The path changes with optional arguments, so use wildcards for scope nodes, such as `minecraft.selector.player.effect.targets.*`.

- Scope, limit and weight apply to every player or entity argument, whether you type a name or a selector. `minecraft.selector` only controls the `@` syntax.
- Fake scoreboard names are not players or entities. Only the limit applies to them.
- The whole selection is checked before the command runs, so a partly denied `@a` changes nothing.
- Random selectors (`@r`, or any selector with `sort=random`) are checked against every entity they could pick. A weight rule denies `/kill @r` if any possible target outranks the sender. The target limit counts the targets actually selected.
- On Spigot and Paper 1.13 - 1.20.2, a selector in a score-holder argument is only limit-checked; see [Compatibility](compatibility.md#limits).
- Selectors inside `/execute` forks are not fully checked; see [Compatibility](compatibility.md#limits).

## Selector limits and weights

Limits and weights are LuckPerms meta, not nodes:

```text
/lp group host meta set minecraft.selector.limit.effect.targets 10
```

Meta has no wildcards, but a shorter key covers every longer form. The plugin looks up the full key, then drops the last part and tries again, down to `minecraft.selector.limit.<command>.<argument>`. A longer key that is set wins.

- `minecraft.selector.limit.effect.targets` caps `/effect give` and `/effect clear`.
- `minecraft.selector.limit.effect.targets.give` caps only `/effect give`.

Weight works the same way. You cannot target players whose weight for the same key is higher than yours. It is only checked if you have a weight, and only for targets whose LuckPerms data is loaded, normally online players.

To find the key for a command:

```text
/lp verbose on Steve & minecraft.selector
```

Run the command with a selector as Steve, then `/lp verbose off`. Read the `meta: minecraft.selector.limit...` line. Set that key, or any shorter one that starts the same way.

With other permissions plugins, limits and weights come from Vault chat meta; see [Permissions plugins](providers.md).

## Bypass nodes

| Node | Lets the player… |
| --- | --- |
| `minecraft.bypass.whitelist` | Join while not whitelisted. |
| `minecraft.bypass.player-limit` | Join when the server is full. |
| `minecraft.bypass.force-gamemode` | Keep their game mode when the server forces the default game mode. |
| `minecraft.bypass.move-speed.player` | Skip the "moved too quickly" and "moved wrongly" checks. |

Whitelist and player limit are checked at login. For GroupManager and other plugins that attach permissions late, see [Other permissions plugins](providers.md#other-permissions-plugins). Force-gamemode and move-speed need Paper; see [What works where](compatibility.md#what-works-where). Folia ignores the player-limit bypass.

## Operator blocks and the debug stick

These nodes only take access away. `false` denies; `true` and unset leave vanilla or Paper in charge.

| Node | Controls |
| --- | --- |
| `minecraft.operator_block.<command_block\|structure_block\|jigsaw\|test_block\|test_instance_block>.<place\|view\|break>` | Placing, opening and breaking the block. `command_block` also covers repeating and chain command blocks. `test_block` and `test_instance_block` exist on 1.21.5 and later. |
| `minecraft.debug_stick.use.<namespace>.<block>` | Using the debug stick on one block. Replace `:` in the block id with `.`, e.g. `minecraft.debug_stick.use.minecraft.oak_trapdoor`. `minecraft.debug_stick.use.*` covers every block. Covers left and right clicks and both hands. |

`view` means opening the block's screen. Vanilla opens it only for a creative player who right-clicks with the main hand and is not sneaking with an item, so `view` covers every way to open it. There is no `edit` action; deny `view` instead. The debug stick nodes need 1.13 or later.

To give access, use Paper's own nodes. They are Paper-only. The deny nodes above still apply.

| Paper's node | Default | Allows |
| --- | --- | --- |
| `minecraft.commandblock` | operator | A non-operator in creative mode to open, edit and break command blocks. Placing still needs operator. |
| `minecraft.debugstick` | operator | The debug stick in creative mode. |
| `minecraft.debugstick.always` | nobody | The debug stick in any game mode. |

Structure blocks and jigsaws are operator and creative only, and have no Paper node.

Paper's `minecraft.admin.command_feedback` (operators have it by default) decides who sees the "[Name: did X]" broadcast of other players' commands while the `sendCommandFeedback` game rule is true. It replaces the original mod's `minecraft.adminbroadcast.receive`, which this plugin does not add.

## Wildcards

- Wildcards need the LuckPerms `apply-wildcards` option, which is on by default.
- `minecraft.command.time.*` matches every node below `minecraft.command.time`, but not `minecraft.command.time` itself.
- A node set directly beats a wildcard, but only for that node. `minecraft.command.* false` plus `minecraft.command.time true` still denies `/time query`, because the wildcard covers `minecraft.command.time.query`. Add `minecraft.command.time.* true` to allow the sub-commands.
- Wildcards do not apply to meta keys.
- GroupManager wildcards do not work; see [GroupManager](providers.md#groupmanager).

### Allowing only a list of vanilla commands

To take every vanilla command away from a group of operators, deny the wildcard and grant back root and `.*` for each command you want, here `/time`:

```text
/lp group admin permission set minecraft.command.* false
/lp group admin permission set minecraft.command.time true
/lp group admin permission set minecraft.command.time.* true
```

Plugin commands are not affected. Ordinary players do not need this, because they have no vanilla operator command until you grant one.

### What `minecraft.command.* false` also turns off

Paper gives some commands to everyone through nodes the wildcard also matches. A group with `minecraft.command.* false` loses `/me`, `/msg`, `/tell`, `/w`, `/teammsg`, `/tm`, `/trigger` and `/minecraft:help`. It also loses Paper's `minecraft.command.selector`, so `@` selectors stop working. Bukkit's own `/help` is not affected. To restore them:

```text
/lp group admin permission set minecraft.command.{me,msg,tell,w,teammsg,tm,trigger,help} true
/lp group admin permission set minecraft.command.{me,msg,teammsg,trigger}.* true
/lp group admin permission set minecraft.selector true
```

The `{a,b}` form is LuckPerms shorthand, on by default through `apply-shorthand`. Without it, write one line per node. `/tell`, `/w` and `/tm` need no `.*` line of their own. Leave out the last line if the group should not use `@` selectors.

## Defaults

What an unset node does, by kind:

| Node kind | Unset |
| --- | --- |
| Command nodes | Vanilla behaviour. |
| `minecraft.selector` | Operators only. |
| Selector scope nodes | Allowed. |
| Limit and weight meta | No limit, no weight check. |
| Bypass nodes | No bypass, operators included. |
| Operator block and debug stick nodes | Vanilla or Paper behaviour. They only deny, so `true` and unset are the same. |

- Operators have every node that is not explicitly `false`, except the bypass nodes.
- Open to everyone in vanilla: `/me`, `/msg` (`/tell`, `/w`), `/teammsg` (`/tm`), `/trigger` and `/minecraft:help`. Plain `/help` is Bukkit's.
- Bypass nodes only grant. `false` and unset both mean no bypass: a player who is not whitelisted cannot join, and a full server stays full.
- `minecraft.selector` defaults to operators. It also grants Paper's `minecraft.command.selector`, so granting `minecraft.selector` is enough on every version.
- Commands run from the console or by a command block are never checked against command or selector nodes.
- This plugin's own nodes are listed in [Nodes this plugin adds](commands.md#nodes-this-plugin-adds).

## What players see when denied

Denied commands and branches also disappear from the client's command list and tab completion, except on 1.8.8. How fast the list updates depends on your permissions plugin; see [Permissions plugins](providers.md).

| Server | Denied command | Denied sub-command or argument |
| --- | --- | --- |
| Paper, Folia, Purpur and Pufferfish 1.20.6+ | Not in tab completion. Typing it gives `Unknown or incomplete command, see below for error`. | Not in tab completion. Typing it gives `Unknown or incomplete command, see below for error` or `Incorrect argument for command`. |
| Spigot 1.13+, Paper 1.13 - 1.20.5 | The server's no-permission message. Aliases such as `/tp` get it too. | Not in tab completion. Typing it gives `Unknown or incomplete command, see below for error` or `Incorrect argument for command`. |
| 1.8.8 - 1.12.2 | The server's no-permission message. | Red `You do not have permission to use this command.` |

On Spigot the no-permission message is `messages.no-permission` in `spigot.yml`. The default is `I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.`

A failed selector check shows a red message, one of:

- `You can't use @ selectors such as @a.`
- `You can target at most N players or entities here.` (`1 player or entity` when the limit is 1.)
- `You can't target non-player entities here.`
- `You can't target other players here.`
- `You can't target yourself here.`
- `You can't target players ranked above you.`
- `You can't target that here.` The plugin could not tell what the selector picked, so it refuses it.

A denied operator block or debug stick action is cancelled with a red chat message, for example `You can't open command blocks.` or `You can't use the debug stick on this block.`

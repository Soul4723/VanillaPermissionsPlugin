# VanillaPermissionsPlugin

A drop-in Paper port of [DrexHD's VanillaPermissions](https://github.com/DrexHD/VanillaPermissions). It lets LuckPerms decide, node by node, who may use each part of every vanilla command (`/gamemode creative`, `/time set`, `/tp <player>`…), whom they may target with names and `@` selectors, and a few vanilla operator features. Paper still parses and runs the commands itself; the plugin only adds permission checks to the existing vanilla command tree.

## Requirements and installation

- Paper **1.20.6** (the only tested version) on Java 21
- [LuckPerms](https://luckperms.net/) 5.5 or newer

1. Put `VanillaPermissionsPlugin-<version>.jar` in `plugins/`. No other plugin, Java agent or startup flag is needed.
2. Restart the server. The log should show `Added LuckPerms checks to <n> vanilla command nodes`.
3. Run `/vanillapermissions debug` to confirm LuckPerms is connected.

If the plugin cannot patch the command tree (for example on another Paper version), it logs the error and disables itself rather than leaving commands half-checked.

## How a node is resolved

Every check asks LuckPerms for the node in the player's current context:

| LuckPerms value | Result |
| --- | --- |
| `true` | Allowed, even for non-operators. |
| `false` | Denied, even for operators (for bypass nodes: no bypass). |
| unset | Vanilla behaviour: whatever the server would do without this plugin. |

What "vanilla behaviour" means depends on the node:

- **Command roots** (`minecraft.command.gamemode`) need operator level 2 in vanilla (a few, like `/msg` and `/list`, are open to everyone).
- **Sub-command and argument nodes** (`minecraft.command.gamemode.creative`) are open to anyone who can use the command. **Granting a command therefore grants all of its sub-commands**; set the branches you don't want to `false`.
- **Selector scopes** default to allowed; **limit and weight** default to no restriction.
- **Bypass nodes** only ever add a bypass. `false` and unset both leave vanilla's decision, so operators still pass the whitelist.

Console and command blocks are not affected by command nodes. LuckPerms wildcards (`minecraft.command.time.*`) work if `apply-wildcards` is enabled in LuckPerms (the default).

To see exactly which nodes a command checks, run `/lp verbose on <player>`, have the player run or tab-complete the command, then `/lp verbose off`.

## Permission reference

### Commands

| Node | Controls |
| --- | --- |
| `minecraft.command.<command>` | The command itself, e.g. `minecraft.command.time`. |
| `minecraft.command.<command>.<node>…` | Each sub-command and argument, named as in the vanilla command tree, e.g. `minecraft.command.time.set`, `minecraft.command.time.set.time`, `minecraft.command.teleport.targets.destination`. |
| `minecraft.command.gamemode.<survival\|creative\|adventure\|spectator>` | One game mode. |
| `minecraft.command.gamemode.<mode>.target` | Setting that mode for another player (`/gamemode <mode> <player>`). |

- `minecraft:<command>` aliases (for example `/minecraft:gamemode`) use the same nodes as the command.
- Alias commands such as `/tp` have their own root node (`minecraft.command.tp`); their arguments use the target command's nodes (`minecraft.command.teleport.…`).
- Commands added by other plugins keep their own permissions.

### `/execute` additions

These upstream extensions are added to `/execute` and use normal command nodes:

| Syntax | Node | Effect |
| --- | --- | --- |
| `/execute if\|unless permission <entity> <node>` | `minecraft.command.execute.<if\|unless>.permission…` | Condition on whether the entity has a LuckPerms node (unset counts as not having it). |
| `/execute feedback silent\|console` | `minecraft.command.execute.feedback.<silent\|console>` | Hide command output, or send it to the console. |
| `/execute feedback entity <targets>` | `minecraft.command.execute.feedback.entity.targets` | Send command output to other entities. Permissions are still checked for the original player. |
| `/execute oplevel <0-4>` / `oplevel entity <targets>` | `minecraft.command.execute.oplevel…` | Run with a lower operator level (never higher than the caller's). |

### Selectors and targets

| Node | Type | Controls |
| --- | --- | --- |
| `minecraft.selector` | permission | Typing `@` selectors at all. Unset falls back to vanilla (operators only). |
| `minecraft.selector.self.<path>` | permission | Targeting yourself. |
| `minecraft.selector.player.<path>` | permission | Targeting other players. |
| `minecraft.selector.entity.<path>` | permission | Targeting non-player entities. |
| `minecraft.selector.limit.<path>` | integer meta | Maximum number of targets. |
| `minecraft.selector.weight.<path>` | integer meta | Players with a higher weight than yours cannot be targeted. Only applies if you have a weight. |

- `<path>` is the command, the argument name, then every other node of the command as typed: `/gamemode survival @s` → `gamemode.target.survival.target`, `/effect give @s speed 5` → `effect.targets.give.targets.effect.seconds`. Because optional arguments are part of the path, use wildcards such as `minecraft.selector.player.effect.targets.*` to cover every form.
- Scope, limit and weight checks apply to every player or entity argument, whether the player typed a name or a selector. `minecraft.selector` only controls `@` syntax.
- Fake scoreboard names are not players or entities; only the limit applies to them.

### Other nodes

| Node | Controls |
| --- | --- |
| `minecraft.bypass.whitelist` | Join while not whitelisted. |
| `minecraft.bypass.player-limit` | Join when the server is full. |
| `minecraft.bypass.force-gamemode` | Keep your game mode when the server forces the default one. |
| `minecraft.bypass.move-speed.player` | Skip the "moved too quickly" and "moved wrongly" checks. |
| `minecraft.operator_block.<command_block\|structure_block\|jigsaw>.<place\|view\|break>` | Operator blocks. Set to `false` to take access away; vanilla still requires operator status and creative mode, so `true` cannot give access to non-operators. |
| `minecraft.debug_stick.use.block` | Using the debug stick on blocks. Same rules as operator blocks. |

### Plugin nodes

| Node | Default | Controls |
| --- | --- | --- |
| `vanillapermissions.use` | op | `/vanillapermissions` help. |
| `vanillapermissions.admin` | op | `/vanillapermissions check` and `debug` (includes `vanillapermissions.use`). |

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
```

The limit is set for the forms `/effect give <targets> <effect>` and `… <seconds>`; add the longer forms the same way if needed.

## What players see when denied

- **Command nodes:** denied commands and branches disappear from the player's command list and tab completion, and typing them gives vanilla's "Unknown or incomplete command". Tab completion updates automatically when LuckPerms data changes.
- **Selector checks:** the command fails with a red message such as `You do not have permission to target other players here` or `You can select at most 5 targets here`. The whole selection is checked first, so a mixed `@a` changes nothing.
- **Operator blocks and debug stick:** the action is cancelled with a red message.

## Plugin commands

- `/vanillapermissions check <player> [node]` shows how LuckPerms resolves a node for an online player in their current context (`true`, `false` or `unset`, or the meta value for limit and weight nodes). Without a node it shows a short summary.
- `/vanillapermissions debug` shows the plugin and server version, whether LuckPerms is connected and how many vanilla command nodes carry checks.

## Limitations

- Only Paper 1.20.6 is tested. Other versions log a warning and the plugin disables itself if the command tree differs.
- Operator block and debug stick nodes can only take access away (see above).
- The Paper plugin API does not expose upstream's spawn-protection, chat-spam, admin-feedback recipient, NBT query, command block minecart or debug chart hooks, so those nodes are not available.
- Selector weight is only compared for targets whose LuckPerms data is loaded (normally online players); offline targets in commands like `/ban` are not weight-checked.

## Troubleshooting

- **I granted a command but the player still can't use part of it.** Check `/lp verbose` for a `false` on a child node or an inherited group. Using `@` selectors also needs `minecraft.selector`, and targeting needs the selector scope nodes not to be `false`.
- **The player can do more than I wanted.** Granting a command grants every sub-command and target; set the unwanted branches or selector scopes to `false`.
- **An operator lost access.** An explicit `false` beats operator status. Look for the node in the operator's own and inherited groups.
- **The plugin disabled itself at startup.** The log explains why. Usually the server is not Paper 1.20.6 or LuckPerms failed to load.

## Building from source

```text
mvn verify
```

Requires JDK 21 and Maven. The plugin jar is written to `target/`; the unit tests run as part of the build.

## License

MIT. See [LICENSE](LICENSE).

# The /vanillapermissions command

The plugin adds one command, `/vanillapermissions`. It shows how your nodes resolve.

| Node | Lets you |
| --- | --- |
| `vanillapermissions.use` | See the command's help. |
| `vanillapermissions.admin` | Run `check` and `debug`. It includes `use`. |

Both default to operators.

## `/vanillapermissions check`

```text
/vanillapermissions check <player> [node]
```

This shows whether an online player has a node, as your permissions plugin answers for them right now. The answer is `true`, `false`, or `unset` followed by what unset means for that node. The meanings are in [True, false and unset](how-it-works.md#true-false-and-unset) and [Defaults](permissions.md#defaults).

```text
/vanillapermissions check Steve minecraft.command.time.set
```

```text
Steve: minecraft.command.time.set = unset (vanilla behaviour applies)
```

- **No effect here:** when a node does nothing on your server, the line ends with `(no effect on this server: …)`. Examples are a sub-command node the 1.8.8 - 1.12.2 checks do not know, a debug stick node before 1.13, and the force-gamemode and move-speed bypasses on Spigot.
- **Paper's own nodes:** `minecraft.commandblock`, `minecraft.debugstick`, `minecraft.debugstick.always` and `minecraft.admin.command_feedback` are recognised. So are the plugin's nodes below. Their unset text is `Paper's default: operators`, `Paper's default: nobody` or `this plugin's default: operators`. With LuckPerms the registered default is already applied, so you see `true` for an operator and `false` for everyone else.
- **Other nodes:** any other node shows its value followed by `(not a node this plugin checks)`. This usually means a typo.
- **Limits and weights:** for a `minecraft.selector.limit.…` or `minecraft.selector.weight.…` node you see the meta value, for example `5 (meta)`. If the value came from a shorter key, the line names that key. When nothing is set you see `unset (no limit)` or `unset (no weight check)`. See [Selector limits and weights](permissions.md#selector-limits-and-weights).

If the player is not online, you get `<player> is not online. You can only check online players.` Without a node, the command prints a summary of a few nodes: `minecraft.selector`, the gamemode and teleport roots, and the whitelist and player-limit bypasses. It shows what your permissions plugin answers, defaults included. An operator sees `true` for almost every node, except the bypass nodes, which are `false` unless you set them. A player who is not an operator sees `false` for the command roots that Paper gives to operators.

```text
Nodes for Steve (operator: no)
  minecraft.selector = false
  minecraft.command.gamemode = false
  minecraft.command.teleport = false
  minecraft.command.tp = false
  minecraft.bypass.whitelist = false
  minecraft.bypass.player-limit = false
Check any node with /vanillapermissions check Steve <node>
```

The exact values depend on your permissions plugin and your groups.

## `/vanillapermissions debug`

This shows what the plugin found on your server:

- the plugin version
- the server software and version, marked tested or untested
- the mode, as in the [startup log](troubleshooting.md#reading-the-startup-log)
- where node values and selector limits and weights come from: LuckPerms and its version, a Bukkit permissions plugin, Vault chat meta, or nothing
- how many vanilla command nodes carry checks, on the `Permission checks added:` line

Include this output when you [report a problem](troubleshooting.md#reporting-a-problem).

## Nodes this plugin adds

| Node | Default | Controls |
| --- | --- | --- |
| `vanillapermissions.use` | operator | `/vanillapermissions` help. |
| `vanillapermissions.admin` | operator | `/vanillapermissions check` and `debug`. Includes `vanillapermissions.use`. |

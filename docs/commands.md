# Commands

The plugin adds one command, `/vanillapermissions`. It needs `vanillapermissions.use` (help) or `vanillapermissions.admin` (`check` and `debug`); both default to operators, and `admin` includes `use`.

## `/vanillapermissions check <player> [node]`

Shows how a node resolves for an online player in their current context: `true`, `false`, or `unset` followed by what unset means for that node. The meaning per node family is the table in [How a node is resolved](permissions.md#how-a-node-is-resolved); for limit and weight nodes the output is the meta value or `unset (no limit)` / `unset (no weight check)`.

- Paper's own nodes that the docs tell you to set (`minecraft.commandblock`, `minecraft.debugstick`, `minecraft.debugstick.always`, `minecraft.admin.command_feedback`) and this plugin's own nodes (below) are recognised too. Their unset text is `Paper's default: operators`, `Paper's default: nobody` or `this plugin's default: operators`. With LuckPerms the registered default is already applied, so you see `true` for an operator and `false` for everyone else.
- Any other node shows its value followed by `(not a node this plugin checks)`, which usually means a typo.

Without a node it shows a summary of a few nodes. It reports the permissions plugin's answer, including the defaults described in [Operator defaults](permissions.md#operator-defaults): an operator shows `true` for almost every node (not the bypass nodes, which are `false` unless set), and a non-operator shows `false` for command roots that Paper gives to operators.

## `/vanillapermissions debug`

Shows the plugin and server version (tested or not), where node values and selector limits and weights come from (LuckPerms and its version, a Bukkit permissions plugin, Vault chat meta, or nothing), and how many vanilla command nodes carry checks.

## Nodes this plugin adds

| Node | Default | Controls |
| --- | --- | --- |
| `vanillapermissions.use` | operator (`op`) | `/vanillapermissions` help. |
| `vanillapermissions.admin` | operator (`op`) | `/vanillapermissions check` and `debug`. Includes `vanillapermissions.use`. |

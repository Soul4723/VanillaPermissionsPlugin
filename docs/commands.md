# Commands

The plugin adds one command, `/vanillapermissions`. It needs `vanillapermissions.use` (help) or `vanillapermissions.admin` (`check` and `debug`); both default to operators, and `admin` includes `use`.

## `/vanillapermissions check <player> [node]`

Shows how a node resolves for an online player in their current context: `true`, `false`, or `unset` followed by what unset means for that node family:

- `unset (vanilla behaviour applies)` for `minecraft.command.…`, `minecraft.selector`, `minecraft.operator_block.…` and `minecraft.debug_stick.use.…`.
- `unset (allowed)` for `minecraft.selector.<self|player|entity>.…`.
- `unset (no bypass)` for `minecraft.bypass.…`.
- For limit and weight nodes, the meta value or `unset (no limit)` / `unset (no weight check)`.
- Any other node shows its value followed by `(not a node this plugin checks)`, which usually means a typo.

Without a node it shows a summary of a few nodes. It reports the permissions plugin's answer, including the defaults described in [Operator defaults](permissions.md#operator-defaults): an operator shows `true` for almost every node (not the bypass nodes, which are `false` unless set), and a non-operator shows `false` for command roots that Paper gives to operators.

## `/vanillapermissions debug`

Shows the plugin and server version (tested or not), where node values and selector limits and weights come from (LuckPerms and its version, a Bukkit permissions plugin, Vault chat meta, or nothing), and how many vanilla command nodes carry checks.

## Plugin nodes

| Node | Default | Controls |
| --- | --- | --- |
| `vanillapermissions.use` | operator (`op`) | `/vanillapermissions` help. |
| `vanillapermissions.admin` | operator (`op`) | `/vanillapermissions check` and `debug`. Includes `vanillapermissions.use`. |

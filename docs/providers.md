# Permissions plugins

LuckPerms is recommended and gives you everything. Any other Bukkit permissions plugin works for nodes. With one of those, [Vault](https://github.com/MilkBowl/Vault) adds selector limits and weights, and lets the whitelist and full-server bypasses work at login.

## Support matrix

CI and the live tests cover LuckPerms. GroupManager 3.2 with Vault 1.7.3 was also checked by hand. The other rows follow from how the plugin reads permissions.

| Provider | Nodes | Limits and weights | Command list refresh | Whitelist and player-limit bypass |
| --- | --- | --- | --- | --- |
| LuckPerms | Contexts and wildcards | LuckPerms meta | Live | Yes |
| GroupManager with Vault (tested) | No wildcards | Vault chat info, not tested | Live, GroupManager resends | Yes, through Vault |
| Other permissions plugin with Vault (untested) | Yes | If Vault has chat info | If the plugin resends | If it attaches at login or backs Vault |
| Other permissions plugin without Vault | Yes | Off | Plugin-dependent | If it attaches at login |
| None | Bukkit defaults and operators | Off | On operator change or rejoin | Bukkit defaults only |

## LuckPerms

LuckPerms 5.5 or newer is recommended. Use 5.4.x on Java 8. It gives you contexts, wildcards, meta for limits and weights, and the bypass checks at login.

- Tab completion updates live, because LuckPerms resends a player's command list after a change. This is its `update-client-command-list` option, on by default. If you turned it off, the list updates when the player rejoins.
- If LuckPerms is installed but failed to start, the plugin logs that and falls back to Bukkit permissions.
- Wildcards need the LuckPerms option `apply-wildcards`, on by default. See [Wildcards](permissions.md#wildcards).

## Other permissions plugins

Any plugin that works through Bukkit's permission API answers true, false or unset for a node. PermissionsEx and GroupManager are examples. These plugins have no contexts, and wildcard behaviour depends on the plugin. GroupManager has its own [section](#groupmanager) below.

- **Command list refresh:** the list refreshes when the player rejoins, when their operator status changes, or when the permissions plugin resends it. GroupManager resends it.
- **Selector limits and weights:** they are read from Vault chat info when Vault and a chat provider are installed. Without them, limits and weights are off. Vault values are read as text first, then as an integer. A value of the wrong type counts as unset.
- **Whitelist and player-limit bypass:** these are checked while the player logs in. Some plugins attach permissions only after the player joins, and GroupManager is one. For those the plugin asks Vault's permission service, which reads the stored data. Without Vault, these two bypasses need a plugin that attaches permissions at login.
- **Load order:** it does not matter. The plugin picks its permissions source when it enables, and again after all plugins have enabled. The second pick adds the log line `Selected after all plugins enabled: …`, naming the final source. A `/reload` does not pick again.
- **Untested:** VaultUnlocked and Vault 2.

## GroupManager

GroupManager 3.2 with Vault 1.7.3 was tested by hand. GroupManager has no wildcards, so you list every node.

Groups live in `plugins/GroupManager/worlds/<world>/groups.yml`, for example `worlds/world/groups.yml` for the default world. The file has a top-level `groups:` key and one entry per group. In `permissions`, `node` grants, `-node` sets the node to `false`, and `+node` makes an exception to a broader denial.

This group is the explicit-node version of the [staff mode recipe](recipes.md#staff-mode-survival-and-spectator). Add it under `groups:`:

```yaml
groups:
  Staff:
    default: false
    permissions:
    - minecraft.command.gamemode
    - -minecraft.command.gamemode.creative
    - -minecraft.command.gamemode.adventure
    - -minecraft.command.gamemode.survival.target
    - -minecraft.command.gamemode.spectator.target
    inheritance: []
    info:
      build: true
```

Save the file and run `manload`, in the console or as `/manload` in game. Then add a player with `manuadd Steve Staff`.

A player in `Staff` can switch themselves between survival and spectator. They cannot use other game modes or change other players.

How GroupManager nodes behave here:

- `-node` shows as `false` and only affects its own node. With `minecraft.command.time`, `minecraft.command.time.query` and `-minecraft.command.time.set` in one group, `/time query daytime` works and `/time set day` is refused. `/time add 1` is unset, so it follows vanilla.
- `+node` shows as `true`.
- Wildcards do nothing. `minecraft.command.*` and `minecraft.command.time.*` leave their sub-nodes unset, so list each node.
- A `-minecraft.command.*` in a group also hides an explicit grant in the same group, such as `minecraft.command.time`.
- Limits and weights depend on Vault chat info and were not tested with GroupManager.

## No permissions plugin

The plugin still runs and logs a warning. Every node follows Bukkit defaults and operator status. Command nodes follow the defaults Paper registers, bypass nodes are `false`, and `minecraft.selector` is for operators only. Selector limits and weights are off.

`/vanillapermissions debug` shows which source the plugin uses. See [the command page](commands.md#vanillapermissions-debug).

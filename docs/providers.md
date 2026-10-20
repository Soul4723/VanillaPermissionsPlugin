# Permissions plugins

LuckPerms is optional. The plugin reads nodes through Bukkit's permission API, and uses LuckPerms' API directly when LuckPerms is installed. [Vault](https://github.com/MilkBowl/Vault) is optional and supplies selector limit and weight meta for plugins other than LuckPerms.

## Support matrix

The tested row is **GroupManager 3.2 with Vault 1.7.3**, checked by hand on Paper 1.20.6 and 1.21.11. CI covers only LuckPerms; every other row is by reading the code (or, where marked, untested).

| Provider | Nodes (true/false/unset) | Selector limit/weight meta | Live command-list refresh | Whitelist / player-limit bypass |
| --- | --- | --- | --- | --- |
| LuckPerms | Yes, with contexts and wildcards | LuckPerms meta | Yes | Yes |
| GroupManager + Vault (tested) | Yes, without wildcards | Vault chat info (not tested) | Yes (GroupManager resends) | Yes, via Vault |
| Other Bukkit permissions plugin + Vault (untested) | Yes | If it provides Vault chat | Only if the plugin resends commands | If it attaches permissions by login or backs Vault |
| Bukkit permissions plugin without Vault | Yes | Off | Plugin-dependent | Only if it attaches by login |
| None | Bukkit defaults and operator status | Off | On operator change or relog | Bukkit defaults only |

## LuckPerms

LuckPerms 5.5 or newer is recommended and gives everything: contexts, wildcards, meta for limits and weights, and the bypass checks at login.

- The plugin re-sends a player's command list about half a second after user data, group data or contexts change. This overlaps with LuckPerms' own `update-client-command-list` option (default true); it works either way.
- If LuckPerms is installed but failed to start, the plugin logs that and falls back to Bukkit permissions.
- Wildcards need LuckPerms `apply-wildcards` (default on); see [Wildcards](permissions.md#wildcards).

## Other permissions plugins

Any plugin that works through Bukkit's permission API (GroupManager, PermissionsEx and others) answers true, false or unset for nodes, but has no contexts. Wildcard behaviour depends on the plugin: with GroupManager 3.2, `minecraft.command.*` and similar wildcards were not seen by this plugin (sub-nodes showed as unset), so list nodes explicitly. A GroupManager recipe is in [Recipes](recipes.md#groupmanager).

- **Command list refresh:** the list is refreshed when the player rejoins, when their operator status changes, or when the permissions plugin resends it (GroupManager does).
- **Selector limit and weight:** read from Vault chat info if Vault and a chat provider are installed; otherwise limits and weights are off. Vault values are read as text first, then as an integer; a value of the wrong type counts as unset.
- **Whitelist and player-limit bypass:** checked while the player logs in. Plugins that attach permissions only after join (GroupManager does) are queried through Vault's permission service, which reads the stored data. Without Vault, these two bypasses need a plugin that attaches permissions at login.
- **Load order:** the plugin picks its permissions source once, in `onEnable`, and softdepends only on LuckPerms and Vault. Node checks themselves read Bukkit's permission API when a command runs, so they do not depend on load order. The pieces that use Vault do: selector limits and weights, and the login-time whitelist and player-limit lookup are connected only if Vault and the permissions plugin have already registered their services when this plugin starts. If `/vanillapermissions debug` shows limits and weights as off with Vault installed, check the startup log for the order the plugins enabled in. GroupManager enabled before this plugin on every start tried here; a start with the permissions plugin enabling after this plugin could not be forced, so that case is read from the code, not observed.
- VaultUnlocked and Vault 2 are untested.

## No permissions plugin

The plugin still runs, logs a warning, and every node follows Bukkit defaults and operator status: command nodes follow the defaults Paper registers, bypass nodes are `false`, and `minecraft.selector` is operator-only. Selector limits and weights are off.

`/vanillapermissions debug` shows which source the plugin is using for nodes and for selector meta.

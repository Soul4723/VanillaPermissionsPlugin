# Troubleshooting

## A command ignores my nodes

- **Another plugin owns the command name.** EssentialsX has its own `/gamemode`, `/time` and `/tp`. Then `/gamemode` follows the EssentialsX permissions. The vanilla command is still there as `/minecraft:gamemode`, and the nodes on this site apply to it.
- **Plugin and Bukkit commands are never affected.** `/lp`, `/help` and `/version` keep their own permissions, even under `minecraft.command.*`.
- **On Spigot 1.13 and later,** a vanilla command that another plugin replaced is left unchecked. The plugin logs a warning about it at startup.

See [Commands from other plugins](how-it-works.md#commands-from-other-plugins).

## A player still can't use part of a command

- Run `/vanillapermissions check Steve <node>` for the node you expect. See [the command page](commands.md#vanillapermissions-check).
- Or run `/lp verbose on Steve & minecraft.command.<command>`, have Steve try again, then run `/lp verbose off`.
- Look for a `false` on a sub-command node, or one inherited from a group.
- Commands with `@` selectors also need [`minecraft.selector`](permissions.md#selectors), and the selector scope nodes must not be `false`.

## A player can do more than I wanted

- Granting a command opens all its sub-commands and targets. See [Granting a command opens its sub-commands](how-it-works.md#granting-a-command-opens-its-sub-commands).
- Set the unwanted sub-commands, or the selector scopes, to `false`. To allow only a few parts, see [Allowing only a few sub-commands](how-it-works.md#allowing-only-a-few-sub-commands).

## An operator lost a command

- A `false` node beats operator status. See [Operators](how-it-works.md#operators).
- Look for the node in the operator's own groups and in the groups they inherit from, including wildcards.

## Players can't use @a or @p

- Players who are not operators need `minecraft.selector`.
- On Paper 1.21.3 and later, a player without it sees `Selector not allowed`.
- If it is granted and a selector still fails, a selector scope node may be `false`. See [Selectors](permissions.md#selectors).

## Tab completion didn't update

- With LuckPerms the list updates live. If you turned off its `update-client-command-list` option, it updates when the player rejoins.
- With other plugins the list refreshes on rejoin, on an operator change, or when the plugin resends it. See [Other permissions plugins](providers.md#other-permissions-plugins).

## A bypass node does nothing

- Bypass nodes are grant-only and default to `false`. Check with `/vanillapermissions check Steve <node>`. See [Bypass nodes](permissions.md#bypass-nodes).
- With a plugin other than LuckPerms, the whitelist and player-limit bypasses have extra requirements. See [Other permissions plugins](providers.md#other-permissions-plugins).
- The force-gamemode and move-speed bypasses need Paper. See [Limits](compatibility.md#limits).

## The plugin disabled itself at startup

- The log says why. See [Reading the startup log](#reading-the-startup-log).
- Usually the server version is not supported. See [Supported servers](compatibility.md#supported-servers).
- Otherwise something removed a vanilla command.
- While the plugin is disabled, vanilla commands work as they would without it.

## /minecraft:<command> behaves differently from /<command>

Another plugin owns the plain name. See [A command ignores my nodes](#a-command-ignores-my-nodes).

## Reading the startup log

On Paper 1.21.11 with LuckPerms, the plugin logs these lines. `<n>` is the number of command nodes and depends on the version.

```text
Using Paper mode on Paper 1.21.11 (checks on every vanilla command and sub-command)
Using LuckPerms <version> for permissions and for selector limits and weights.
Added /execute if permission, feedback and oplevel.
Added permission checks to <n> vanilla command nodes
```

The mode line says how the plugin adds its checks:

- `Paper mode` is used on Paper, Folia, Purpur and Pufferfish 1.20.6 and later.
- `Spigot mode` is used on Spigot 1.13 and later, and on Paper 1.13 - 1.20.5. For example: `Using Spigot mode on Paper 1.16.5`.
- `legacy mode` is used on 1.8.8 - 1.12.2.

On 1.8.8 - 1.12.2 the mode line reads `Using legacy mode on Spigot 1.12.2 (sub-command checks for 10 commands only; other vanilla commands keep their root node)`. There is no `/execute` line. The line `Added permission checks to <n>` appears one tick after startup, after the server's `Done (...)!` line. `<n>` is small, over a hundred rather than over seven hundred.

On Spigot, an INFO line lists the bypass nodes that need Paper and do nothing there. See [Limits](compatibility.md#limits).

`/vanillapermissions debug` repeats the mode, the server version, the permissions plugin and the node count.

These lines mean a problem:

- **`Could not add permission checks to vanilla commands on <software> <version> (tested: ...), so VanillaPermissionsPlugin is disabling itself and vanilla commands work as they would without it.`** This is SEVERE. The plugin could not add its checks to this server version. If the version is in the [supported list](compatibility.md#supported-servers), report it with the full startup log.
- **`<software> <version> is not supported (<reason>), so VanillaPermissionsPlugin is disabling itself and leaving vanilla commands unchanged`** This is SEVERE. The software or version is not supported, for example `Folia is supported from 1.20.6` or `not a CraftBukkit-based server`. See [Supported servers](compatibility.md#supported-servers).
- **`No permissions plugin found. Operators keep vanilla commands; install LuckPerms to set nodes.`** This is a WARNING. The checks are installed, but you cannot give anyone a node. Install LuckPerms or another permissions plugin. See [Permissions plugins](providers.md).

- **`LuckPerms is not working, so permissions come from Bukkit for now. Fix the LuckPerms errors above and restart.`** This is SEVERE. LuckPerms is installed but the plugin could not use it. Fix LuckPerms first; until then nodes come from Bukkit.

`Now using <source> for permissions (it loaded after this plugin).` is not a problem. A permissions plugin enabled after this one, and the plugin picked it up.

Limits such as unsupported versions and features missing on some servers are listed in [Compatibility](compatibility.md#limits). Folia notes are in [Folia](compatibility.md#folia).

## Reporting a problem

Not sure it is a bug, or stuck on a setup? Ask in [Discussions](https://github.com/Soul4723/VanillaPermissionsPlugin/discussions/categories/q-a).

Open an issue from the [issue templates](https://github.com/Soul4723/VanillaPermissionsPlugin/issues/new/choose). Include:

- the output of `/vanillapermissions debug`
- the output of `/vanillapermissions check <player> <node>` for the node involved
- the full startup log, if the plugin disabled itself

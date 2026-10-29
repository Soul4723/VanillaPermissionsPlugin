# How permissions work

This page explains the idea behind every node. It takes five minutes, and the [recipes](recipes.md) make sense after it.

## A node for every command and sub-command

Each vanilla command has a node, and so does each part of it:

| You type | Nodes checked |
| --- | --- |
| `/time set day` | `minecraft.command.time`, `minecraft.command.time.set`, `minecraft.command.time.set.day` |
| `/gamemode creative` | `minecraft.command.gamemode`, `minecraft.command.gamemode.creative` |
| `/gamemode creative Alex` | the two above, then `minecraft.command.gamemode.creative.target` |

You set these nodes in LuckPerms like any other permission. A player can run a command only if every node it checks is allowed.

## True, false and unset

| Value | Effect |
| --- | --- |
| `true` | Allowed, even for a player who is not an operator. |
| `false` | Denied, even for an operator. |
| unset | Vanilla behaviour. On a command: operators can use it, other players cannot. On a sub-command: anyone who can use the command can use it. |

So nothing changes until you set a node. A few commands, such as `/msg`, `/me` and `/trigger`, are open to everyone in vanilla and stay open.

## Granting a command opens its sub-commands

When you grant a command, its sub-commands are still unset, so they open with it. Deny the parts you want to keep closed:

```text
/lp group builder permission set minecraft.command.time true
/lp group builder permission set minecraft.command.time.set false
```

Builders can now check the time with `/time query` and skip ahead with `/time add`, but cannot use `/time set`.

## Allowing only a few sub-commands

To allow only one or two parts of a command, close everything below it with a `.*` wildcard and then open the parts you want:

```text
/lp group staff permission set minecraft.command.gamemode true
/lp group staff permission set minecraft.command.gamemode.* false
/lp group staff permission set minecraft.command.gamemode.spectator true
```

Staff can now use `/gamemode spectator` on themselves. Every other mode is closed, and so is changing another player's mode, because `minecraft.command.gamemode.spectator.target` is still covered by the wildcard. A node set directly always wins over a wildcard.

`minecraft.command.gamemode.*` covers the nodes below `minecraft.command.gamemode`, not the node itself. That is why the first line is still needed. Wildcards need the LuckPerms option `apply-wildcards`, which is on by default.

## Operators

Operators keep every vanilla command, as they do without the plugin. A `false` node takes a command away from an operator too. To restrict your operators, put them in a group such as `admin` and set `false` nodes on that group. The [operator recipes](recipes.md#operators) show how.

## Selectors such as `@a` and `@e`

Players who are not operators cannot type `@` selectors, even in a command you granted. Grant `minecraft.selector` to allow them. You can also stop a group from targeting other players or entities, and cap how many targets one command may hit. See [Selectors](permissions.md#selectors).

## Commands from other plugins

These nodes cover vanilla commands only. Commands added by other plugins keep their own permissions.

Some plugins replace a vanilla command. EssentialsX, for example, has its own `/gamemode`, `/time` and `/tp`. Then `/gamemode` follows the EssentialsX permissions, and the vanilla command is still reachable as `/minecraft:gamemode` with the nodes on this site. See [Troubleshooting](troubleshooting.md#a-command-ignores-my-nodes).

## Finding the node for a command

- Tab completion only offers what a player may use, and it updates as soon as you change a node in LuckPerms. Log in as a test player and press Tab.
- `/lp verbose on Steve & minecraft.command` prints every command node checked while Steve plays. Have Steve run the command, then run `/lp verbose off`.
- `/vanillapermissions check Steve minecraft.command.time.set` shows whether Steve has a node and why. See [The /vanillapermissions command](commands.md).

Every node is listed in [Permission nodes](permissions.md).

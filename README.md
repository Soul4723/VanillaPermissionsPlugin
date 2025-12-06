# ExtraPermissions

A Paper plugin that adds comprehensive permission checks to vanilla commands and features.

* You need to have a permissions plugin installed (e.g. [LuckPerms](https://luckperms.net))
  (Any permission provider that supports the Bukkit permissions system is supported.)

## Features

- **Command Permissions** - Fine-grained control over all vanilla commands
- **Bypass Permissions** - Allow players to bypass spawn protection, chat/movement speed limits, whitelist, and player limit
- **Operator Block Control** - Manage access to command blocks, jigsaw blocks, and structure blocks
- **Selector Permissions** - Control entity and player targeting in commands
- **Debug Tool Permissions** - Restrict debug stick and debug chart usage
- **NBT Permissions** - Control placing blocks/entities with NBT data
- **Tab Completion** - Commands are hidden from tab completion unless player has permission
- **Admin Broadcast** - Optional command feedback broadcast to admins

## Permissions

| Permission | Description |
|---|---|
| `minecraft.adminbroadcast.receive` | Receive broadcast of other players' commands |
| `minecraft.bypass.spawn-protection` | Build inside spawn protection |
| `minecraft.bypass.force-gamemode` | Bypass forced gamemode |
| `minecraft.bypass.move-speed.player` | Bypass player movement speed limits |
| `minecraft.bypass.move-speed.vehicle.<entity>` | Bypass movement speed limits while in vehicles |
| `minecraft.bypass.chat-speed` | Bypass chat speed limits |
| `minecraft.bypass.whitelist` | Bypass server whitelist |
| `minecraft.bypass.player-limit` | Bypass server player limit |
| [`minecraft.command.<command>`](#commands) | Base command permission |
| `minecraft.debug_stick.use.<block>` | Use debug stick on specific blocks |
| `minecraft.debug_chart` | View debug chart |
| `minecraft.query.<entity/block>` | Query blocks and entities with NBT |
| `minecraft.load.<entity/block>` | Load blocks and entities with NBT data |
| `minecraft.operator_block.command_block.<action>` | Control command blocks (place/view/edit/break) |
| `minecraft.operator_block.jigsaw.<action>` | Control jigsaw blocks |
| `minecraft.operator_block.structure_block.<action>` | Control structure blocks |
| `minecraft.selector` | Use entity selectors in commands |
| `minecraft.selector.entity.<selector>` | Select non-player entities |
| `minecraft.selector.player.<selector>` | Select other players |
| `minecraft.selector.self.<selector>` | Select self |

## Commands

Commands use a node-based permission system similar to Minecraft's op levels. Each command is made up of nodes, and each node has its own permission.

For example, the `/gamemode` command:
- The root node (`/gamemode`) requires `minecraft.command.gamemode`
- Sub-nodes like `survival`, `creative`, etc. use `minecraft.command.gamemode.survival`,
  `minecraft.command.gamemode.creative`, and so on.

Once a player has access to the root node, all sub-nodes are unlocked by default. If you want finer control, you can manually restrict sub-nodes by denying their specific permissions.

#### Example

```yml
Allow:
  minecraft.command.gamemode
Deny:
  minecraft.command.gamemode.creative
  minecraft.command.gamemode.spectator
```

This allows players to use `/gamemode` but restricts them to only survival and adventure modes.

## Selectors

By default, granting `minecraft.selector` allows players to use any selector in commands they have access to.

Fine-grained permission control operates on selector scope:

* `minecraft.selector.entity.<selector>` - Select non-player entities
* `minecraft.selector.player.<selector>` - Select other players
* `minecraft.selector.self.<selector>` - Select self

Commands fail if a player attempts to select unauthorized entities.

## Building

```bash
mvn clean package
```

Compiled JAR available in `target/`

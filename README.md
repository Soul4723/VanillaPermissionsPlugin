# VanillaPermissionsPlugin

Adds granular permission control for vanilla Minecraft commands on Paper servers. Lets you control specific gamemode types, teleport options, entity selectors, and more.

Heavily influenced by [VanillaPermissions](https://github.com/DrexHD/VanillaPermissions) by DrexHD.

## Requirements

- Paper 1.20.1+
- [CommandAPI](https://github.com/JorelAli/CommandAPI) 9.5.0+
- [LuckPerms](https://luckperms.net) 5.4+

## What It Does

**Gamemode** - Control which gamemodes players can use and whether they can change others
- Give `/gamemode spectator` to staff without giving creative
- Let players change their own gamemode but not others
- Restrict specific modes (creative, survival, adventure, spectator)

**Teleport** - Separate permissions for teleporting to players vs coordinates
- Allow `/tp PlayerName` but block `/tp 100 64 100`
- Control entity and coordinate teleports independently

**Selectors** - Control entity selector usage (@a, @e, @p, @r, @s)
- Block mass-targeting with `@a` or `@e`
- Allow self-targeting with `@s` only
- Separate permissions for player vs entity selectors

**Bypasses**
- Spawn protection - Let specific players build in spawn
- Chat speed - Bypass chat rate limiting
- Whitelist - Join when whitelist is enabled
- Player limit - Join when server is full

**Operator Blocks** - Control command blocks, jigsaw blocks, and structure blocks
- Separate permissions for place, view, and break
- Prevent unauthorized command block usage

**Admin**
- Control who sees command feedback broadcasts
- Restrict debug stick usage

## Permissions

### Commands

#### Gamemode
- `minecraft.command.gamemode` - Base permission
- `minecraft.command.gamemode.survival` - Use survival mode
- `minecraft.command.gamemode.creative` - Use creative mode
- `minecraft.command.gamemode.adventure` - Use adventure mode
- `minecraft.command.gamemode.spectator` - Use spectator mode
- `minecraft.command.gamemode.other` - Change other players
- `minecraft.command.gamemode.<mode>.other` - Change others to specific mode

#### Teleport
- `minecraft.command.teleport` - Base permission
- `minecraft.command.teleport.targets` - Teleport to entities
- `minecraft.command.teleport.targets.location` - Teleport to coordinates

### Selectors
- `minecraft.selector.entity.e` - Use @e selector (all entities)
- `minecraft.selector.player.a` - Use @a selector (all players)
- `minecraft.selector.player.p` - Use @p selector (nearest player)
- `minecraft.selector.player.r` - Use @r selector (random player)
- `minecraft.selector.self.s` - Use @s selector (self)

### Bypasses
- `minecraft.bypass.spawn-protection` - Build in spawn protection
- `minecraft.bypass.chat-speed` - Bypass chat speed kick
- `minecraft.bypass.whitelist` - Bypass whitelist
- `minecraft.bypass.player-limit` - Join when server is full

### Operator Blocks
- `minecraft.operator_block.command_block.<action>` - Control command blocks
- `minecraft.operator_block.jigsaw.<action>` - Control jigsaw blocks
- `minecraft.operator_block.structure_block.<action>` - Control structure blocks

Actions: `place`, `view`, `break`

### Admin
- `minecraft.adminbroadcast.receive` - See command broadcasts
- `minecraft.debug_stick.use.block` - Use debug stick

## Configuration

```yaml
features:
  command_permissions: true
  selector_permissions: true
  bypass_permissions: true
  admin_permissions: true
```

## Commands

- `/extrapermissions reload` (or `/eperm reload`, `/ep reload`) - Reload config
- `/extrapermissions check <player>` - Check player permissions
- `/extrapermissions debug` - View debug info
- `/gamemode <mode> [player]` - Change gamemode with granular permissions
- `/tp <player|entity>` - Teleport to entity
- `/tp <x> <y> <z> [yaw] [pitch]` - Teleport to coordinates

## Notes & Limitations

**Operator Blocks (Command/Jigsaw/Structure)**
- View and interact permissions only apply to players in Creative mode
- Vanilla Minecraft already restricts these blocks to Creative mode
- Most useful for creative servers where you want granular staff permissions

**Debug Stick**
- Only works in Creative mode with cheats enabled
- Vanilla already restricts debug stick usage
- Permission adds an extra layer of control for creative servers

**Selectors**
- Only affects commands that use Minecraft entity selectors (@a, @e, @p, @r, @s)
- Note: `@e` can select players since players are entities
- Custom plugin commands may not respect these permissions

**Spawn Protection**
- Matches vanilla behavior: blocks placing/breaking only
- Players can still interact with doors, chests, buttons, etc.
- Bypass permission allows building in spawn protection radius

**Chat Speed Bypass**
- Detects kick messages containing "chat", "spam", or "too quickly"
- May not work with all server configurations or custom chat plugins
- Consider this feature experimental

**Permission System Behavior**
- OP players bypass ALL permission checks automatically
- Parent permissions grant access to child permissions (e.g., `minecraft.command.gamemode` grants all gamemode sub-permissions)
- Explicit denies (setting permission to `false`) override parent permissions
- Without explicit permissions, non-OP players are blocked from commands (vanilla behavior)

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.

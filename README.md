# VanillaPermissionsPlugin

Paper plugin that adds granular permission control to vanilla Minecraft commands and features.

Heavily influenced by [VanillaPermissions](https://github.com/DrexHD/VanillaPermissions) by DrexHD.

## Requirements

- Paper/Spigot 1.20.1+
- [CommandAPI](https://github.com/JorelAli/CommandAPI) 9.5.0+
- [LuckPerms](https://luckperms.net) 5.4+

## Features

- Granular gamemode control (per-mode, self vs others)
- Teleport permissions (entity, coordinates, rotation)
- Selector permissions (@a, @e, @p, @r, @s)
- Spawn protection bypass
- Chat speed bypass
- Whitelist bypass
- Operator block control (command/jigsaw/structure blocks)
- NBT permissions (opt-in)
- Debug stick restrictions
- Admin command broadcast

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
- `minecraft.selector` - Use selectors
- `minecraft.selector.entity.<selector>` - Select entities
- `minecraft.selector.player.<selector>` - Select players
- `minecraft.selector.self.<selector>` - Select self

### Bypasses
- `minecraft.bypass.spawn-protection` - Build in spawn protection
- `minecraft.bypass.chat-speed` - Bypass chat cooldown
- `minecraft.bypass.whitelist` - Bypass whitelist
- `minecraft.bypass.player-limit` - Bypass player limit

### Operator Blocks
- `minecraft.operator_block.command_block.<action>` - Control command blocks
- `minecraft.operator_block.jigsaw.<action>` - Control jigsaw blocks
- `minecraft.operator_block.structure_block.<action>` - Control structure blocks

Actions: `place`, `view`, `edit`, `break`

### NBT (Opt-in)
- `minecraft.nbt.query.block` - Query block NBT
- `minecraft.nbt.query.item` - Query item NBT
- `minecraft.nbt.modify.block` - Modify block NBT
- `minecraft.nbt.modify.entity` - Modify entity NBT

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
  debug_features: true
  nbt_permissions: false
```

## Commands

- `/extrapermissions reload` - Reload config
- `/extrapermissions check <player>` - Check permissions
- `/extrapermissions debug` - View debug info

## License

MIT

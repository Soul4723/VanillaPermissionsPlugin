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
- Control coordinate and rotation teleports independently

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
- Separate permissions for place, view, edit, and break
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
- `minecraft.command.teleport.targets.location.rotation` - Teleport with rotation

### Selectors
- `minecraft.selector` - Use selectors
- `minecraft.selector.entity.<selector>` - Select entities
- `minecraft.selector.player.<selector>` - Select players
- `minecraft.selector.self.<selector>` - Select self

### Bypasses
- `minecraft.bypass.spawn-protection` - Build in spawn protection
- `minecraft.bypass.chat-speed` - Bypass chat cooldown
- `minecraft.bypass.chat-speed.global` - Bypass all chat speed limits
- `minecraft.bypass.chat-speed.reduced` - Reduced chat cooldown
- `minecraft.bypass.whitelist` - Bypass whitelist
- `minecraft.bypass.player-limit` - Bypass player limit

### Operator Blocks
- `minecraft.operator_block.command_block.<action>` - Control command blocks
- `minecraft.operator_block.jigsaw.<action>` - Control jigsaw blocks
- `minecraft.operator_block.structure_block.<action>` - Control structure blocks

Actions: `place`, `view`, `edit`, `break`

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
```

## Commands

- `/extrapermissions reload` - Reload config
- `/extrapermissions check <player>` - Check permissions
- `/extrapermissions debug` - View debug info

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.

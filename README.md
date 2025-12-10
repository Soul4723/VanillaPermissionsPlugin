# VanillaPermissionsPlugin

Adds granular permission control for vanilla Minecraft commands on Paper servers. Lets you control specific gamemode types, teleport options, entity selectors, and more.

Heavily influenced by [VanillaPermissions](https://github.com/DrexHD/VanillaPermissions) by DrexHD.

## Requirements

- Paper 1.20.6+
- [CommandAPI](https://github.com/CommandAPI/CommandAPI) 11.0.0+
- [LuckPerms](https://luckperms.net) 5.5+

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

#### Whitelist
- `minecraft.command.whitelist` - Base permission
- `minecraft.command.whitelist.add` - Add player to whitelist
- `minecraft.command.whitelist.remove` - Remove player from whitelist
- `minecraft.command.whitelist.on` - Enable whitelist
- `minecraft.command.whitelist.off` - Disable whitelist

#### Time
- `minecraft.command.time` - Base permission
- `minecraft.command.time.set` - Set world time
- `minecraft.command.time.add` - Add time to world
- `minecraft.command.time.query` - Query world time

#### Effect
- `minecraft.command.effect` - Base permission
- `minecraft.command.effect.give` - Give effect to entity
- `minecraft.command.effect.clear` - Clear effects from entity

#### Weather
- `minecraft.command.weather` - Base permission
- `minecraft.command.weather.clear` - Clear weather
- `minecraft.command.weather.rain` - Start rain
- `minecraft.command.weather.thunder` - Start thunderstorm

#### Gamerule
- `minecraft.command.gamerule` - Base permission
- `minecraft.command.gamerule.query` - Query gamerule value
- `minecraft.command.gamerule.set` - Set gamerule value

#### Difficulty
- `minecraft.command.difficulty` - Use /difficulty command

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
- `minecraft.bypass.force-gamemode` - Override server force-gamemode setting
- `minecraft.bypass.move-speed.player` - Bypass player movement speed kicks
- `minecraft.bypass.move-speed.vehicle.boat` - Bypass movement speed kick while in boat
- `minecraft.bypass.move-speed.vehicle.minecart` - Bypass movement speed kick while in minecart

### Operator Blocks
- `minecraft.operator_block.command_block.<action>` - Control command blocks
- `minecraft.operator_block.jigsaw.<action>` - Control jigsaw blocks
- `minecraft.operator_block.structure_block.<action>` - Control structure blocks

Actions: `place`, `view`, `break`

### Admin
- `minecraft.adminbroadcast.receive` - See command broadcasts
- `minecraft.debug_stick.use.block` - Use debug stick

## Installation

1. Download [CommandAPI](https://github.com/CommandAPI/CommandAPI/releases) 11.0.0+
2. Download [LuckPerms](https://luckperms.net/download) 5.5+
3. Download ExtraPermissions
4. Place all three plugins in your `plugins/` folder
5. Restart your server
6. Configure permissions using LuckPerms (`/lp editor`)

## Configuration

```yaml
features:
  command_permissions: true
  selector_permissions: true
  bypass_permissions: true
  admin_permissions: true

default_permissions:
  # Whether OP players automatically bypass all permission checks
  ops_bypass_all: true
  # Minimum OP level required to bypass permission checks (1-4)
  minimum_op_level: 4
```

### Configuration Options

**Features** - Toggle entire feature categories on/off
- `command_permissions` - Enable granular command permissions
- `selector_permissions` - Enable entity selector restrictions
- `bypass_permissions` - Enable bypass permissions (spawn protection, whitelist, etc.)
- `admin_permissions` - Enable admin-related permissions (debug stick, operator blocks, etc.)

**Default Permissions** - Control OP behavior
- `ops_bypass_all` - If true, OP players bypass all ExtraPermissions checks (default: true)
- `minimum_op_level` - Minimum OP level (1-4) required to bypass checks (default: 4)
  - Set to 1-3 to require higher OP levels for bypassing
  - Set `ops_bypass_all: false` to make all OPs respect permissions

## Quick Start

### Understanding `.other` Permissions

For commands like gamemode, there are two ways to control targeting other players:

**Simple Control:**
- `minecraft.command.gamemode.other true` - Can change any player to any gamemode
- `minecraft.command.gamemode.other false` - Cannot change other players at all

**Granular Control:**
- `minecraft.command.gamemode.survival.other true` - Can only change others to survival
- `minecraft.command.gamemode.creative.other false` - Cannot change others to creative
- Set each mode's `.other` permission individually for precise control

### Example: Moderator Setup
Give moderators spectator mode and teleport, can change others to survival only:
```
minecraft.command.gamemode true
minecraft.command.gamemode.survival true
minecraft.command.gamemode.spectator true
minecraft.command.gamemode.creative false
minecraft.command.gamemode.adventure false
minecraft.command.gamemode.survival.other true
minecraft.command.gamemode.spectator.other false
minecraft.command.gamemode.creative.other false
minecraft.command.gamemode.adventure.other false

minecraft.command.teleport true
minecraft.command.teleport.targets true
minecraft.command.teleport.targets.location false
```

### Example: Builder Setup
Let builders bypass spawn protection and use creative mode:
```
minecraft.command.gamemode true
minecraft.command.gamemode.creative true
minecraft.command.gamemode.survival true
minecraft.command.gamemode.spectator false
minecraft.command.gamemode.adventure false
minecraft.command.gamemode.other false

minecraft.bypass.spawn-protection true
```

### Example: Make OPs Respect Permissions
In `config.yml`:
```yaml
default_permissions:
  ops_bypass_all: false
```
Now even OP players must have explicit permissions granted through LuckPerms.

## Commands

### Plugin Commands
- `/extrapermissions reload` (or `/eperm reload`, `/ep reload`) - Reload config
- `/extrapermissions check <player>` - Check player permissions
- `/extrapermissions debug` - View debug info

### Overridden Vanilla Commands
- `/gamemode <mode> [player]` - Change gamemode with granular permissions
- `/tp <player|entity>` - Teleport to entity
- `/tp <x> <y> <z> [yaw] [pitch]` - Teleport to coordinates
- `/whitelist <add|remove|on|off> [player]` - Manage whitelist with permissions
- `/time <set|add|query> [value]` - Control world time
- `/effect <give|clear> <target> [effect] [seconds]` - Manage effects
- `/weather <clear|rain|thunder>` - Control weather
- `/gamerule <query|set> <rule> [value]` - Manage gamerules
- `/difficulty <peaceful|easy|normal|hard>` - Set difficulty

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
- Detects kick messages containing movement speed related keywords
- May not work with all server configurations or custom anti-cheat plugins
- Consider this feature experimental

**Permission System Behavior**
- OP players bypass ALL permission checks by default (configurable via `ops_bypass_all`)
- Set `ops_bypass_all: false` to make OPs use the permission system like regular players
- Parent permissions grant access to child permissions (e.g., `minecraft.command.gamemode` grants all gamemode sub-permissions)
- Explicit denies (setting permission to `false`) override parent permissions
- Without explicit permissions, non-OP players are blocked from commands (vanilla behavior)

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.

# ExtraPermissions

Adds granular permission control for vanilla Minecraft commands on Paper servers. Lets you control specific gamemode types, teleport options, entity selectors, and more.

Heavily influenced by [VanillaPermissions](https://github.com/DrexHD/VanillaPermissions) by DrexHD.

## Installation

1. Install [Paper](https://papermc.io/downloads) 1.20.6+
2. Download [CommandAPI](https://github.com/CommandAPI/CommandAPI/releases) 11.0.0+
3. Download [LuckPerms](https://luckperms.net/download) 5.5+
4. Download [ExtraPermissions](https://github.com/Soul4723/ExtraPermissions)
5. Drop all three in your plugins folder and restart

Configure permissions through LuckPerms with `/lp editor`.

## What does it do?

Gives you actual control over vanilla commands instead of the usual all-or-nothing approach, and a few other nice to haves.

**Commands with granular permissions:**
- `/gamemode`, `/tp`, `/time`, `/weather`, `/gamerule`, `/difficulty`, `/effect` and `/whitelist`

**Entity selectors:**
- Control who can use `@a`, `@e`, `@p`, `@r`, `@s` in commands

**Useful bypasses:**
- Spawn protection
- Chat speed kicks
- Whitelist
- Server full kicks
- Movement speed kicks

**Admin controls:**
- Command blocks, structure blocks, jigsaw blocks (place/view/break)
- Debug stick usage
- Command feedback broadcasts

## Quick Examples

### Example Moderator Setup
```
Group Moderator
minecraft.command.gamemode true
minecraft.command.gamemode.survival true
minecraft.command.gamemode.spectator true
minecraft.command.gamemode.creative false
minecraft.command.gamemode.adventure false
minecraft.command.gamemode.other true
minecraft.command.gamemode.survival.other true
minecraft.command.gamemode.spectator.other false
minecraft.command.gamemode.creative.other false
minecraft.command.gamemode.adventure.other false

minecraft.command.teleport true
minecraft.command.teleport.targets true
minecraft.command.teleport.location false
```

### Example Builder Setup
```
Group Builder
minecraft.command.gamemode true
minecraft.command.gamemode.creative true
minecraft.command.gamemode.survival true
minecraft.command.gamemode.spectator false
minecraft.command.gamemode.adventure false
minecraft.command.gamemode.other false

minecraft.bypass.spawn-protection true
```

### Making OPs respect permissions
In `config.yml`:
```yaml
default_permissions:
  ops_bypass_all: false
```

## How `.other` permissions work

For commands that target players (like gamemode), permissions are checked in this order:

**General permission (checked first):**
- `minecraft.command.gamemode.other` - If false, blocks ALL gamemode changes to other players
- If true, allows changing others to any mode

**Specific mode permissions (checked second):**
- `minecraft.command.gamemode.survival.other` - Additional check for this specific mode
- `minecraft.command.gamemode.creative.other` - Additional check for this specific mode
- etc.

**Both must pass:** To change another player's gamemode, you need BOTH:
1. `minecraft.command.gamemode.other` = true (or parent permission)
2. `minecraft.command.gamemode.<mode>.other` = true (or parent permission)

## Configuration

```yaml
features:
  command_permissions: true    # Granular command controls
  selector_permissions: true   # Entity selector restrictions
  bypass_permissions: true     # Spawn protection, whitelist, etc.
  admin_permissions: true      # Command blocks, debug stick, etc.

default_permissions:
  ops_bypass_all: true         # Set to false to make OPs respect permissions
```

## Commands

**Plugin commands:**
- `/extrapermissions reload` (or `/ep reload`) - Reload config
- `/extrapermissions check <player>` - Check a player's key permissions
- `/extrapermissions debug` - View plugin status and info

**Overridden vanilla commands:**
- `/gamemode <mode> [player]`
- `/tp <target>` - Teleport to entity
- `/tp <x> <y> <z>` - Teleport to coordinates
- `/tp <targets> <destination>` - Teleport entities to another entity
- `/tp <targets> <x> <y> <z>` - Teleport entities to coordinates
- `/time <set|add|query> <value>`
- `/weather <clear|rain|thunder>`
- `/gamerule <query|set> <rule> [value]`
- `/difficulty <peaceful|easy|normal|hard>`
- `/effect <give|clear> <target> [effect]`
- `/whitelist <add|remove|on|off> [player]`

---

## Permissions Reference

### Gamemode
- `minecraft.command.gamemode` - Base permission
- `minecraft.command.gamemode.survival` - Use survival mode
- `minecraft.command.gamemode.creative` - Use creative mode
- `minecraft.command.gamemode.adventure` - Use adventure mode
- `minecraft.command.gamemode.spectator` - Use spectator mode
- `minecraft.command.gamemode.other` - Change other players (any mode)
- `minecraft.command.gamemode.<mode>.other` - Change others to specific mode

### Teleport
- `minecraft.command.teleport` - Base permission
- `minecraft.command.teleport.targets` - Teleport to entities/players
- `minecraft.command.teleport.location` - Teleport self to coordinates
- `minecraft.command.teleport.targets.targets` - Teleport entities to other entities
- `minecraft.command.teleport.targets.location` - Teleport entities to coordinates

### Other Commands
- `minecraft.command.time` + `.set`, `.add`, `.query`
- `minecraft.command.weather` + `.clear`, `.rain`, `.thunder`
- `minecraft.command.gamerule` + `.query`, `.set`
- `minecraft.command.difficulty`
- `minecraft.command.effect` + `.give`, `.clear`
- `minecraft.command.whitelist` + `.add`, `.remove`, `.on`, `.off`

### Selectors
- `minecraft.selector.entity.e` - Use @e (all entities)
- `minecraft.selector.player.a` - Use @a (all players)
- `minecraft.selector.player.p` - Use @p (nearest player)
- `minecraft.selector.player.r` - Use @r (random player)
- `minecraft.selector.self.s` - Use @s (self)

**Note:** Some commands (like `/effect`) also create dynamic selector permissions based on the target type:
- `minecraft.selector.player.effect.targets` - Use player selectors in /effect command
- `minecraft.selector.entity.effect.targets` - Use entity selectors in /effect command

### Bypasses
- `minecraft.bypass.spawn-protection` - Build in spawn protection
- `minecraft.bypass.chat-speed` - Bypass chat speed kick
- `minecraft.bypass.whitelist` - Join when whitelist is on
- `minecraft.bypass.player-limit` - Join when server is full
- `minecraft.bypass.force-gamemode` - Override force-gamemode setting
- `minecraft.bypass.move-speed.player` - Bypass movement speed kicks
- `minecraft.bypass.move-speed.vehicle.boat` - Bypass speed kick in boats
- `minecraft.bypass.move-speed.vehicle.minecart` - Bypass speed kick in minecarts

### Operator Blocks
Replace `<action>` with `place`, `view`, or `break`:
- `minecraft.operator_block.command_block.<action>`
- `minecraft.operator_block.jigsaw.<action>`
- `minecraft.operator_block.structure_block.<action>`

### Admin
- `minecraft.adminbroadcast.receive` - See command feedback
- `minecraft.debug_stick.use.block` - Use debug stick

## Notes

**Permission hierarchy:** Parent permissions grant access to children. For example, `minecraft.command.gamemode` gives access to all gamemode subpermissions unless explicitly denied.

**OP behavior:** By default, OPs bypass all ExtraPermissions checks. Set `ops_bypass_all: false` in config to change this.

**Selectors:** Only affects vanilla entity selectors in commands. Custom plugin commands might not respect these.

**Operator blocks & debug stick:** Only work in Creative mode anyway (vanilla behavior). These permissions add an extra layer for creative servers.

**Movement speed bypasses:** Detection is based on kick message keywords. Might not catch all cases, especially with custom anti-cheat plugins.

## License

MIT License - See `LICENSE` file for details.

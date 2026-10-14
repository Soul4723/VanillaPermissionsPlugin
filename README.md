# VanillaPermissionsPlugin

Paper port inspired by [DrexHD's VanillaPermissions](https://github.com/DrexHD/VanillaPermissions). It uses LuckPerms to grant or deny specific vanilla command actions, modes, targets, and selectors—for example, allowing a player to use survival and spectator for themself while blocking creative, adventure, and changes to other players.

This project targets Paper's supported command and event APIs. Replaced command syntax and behavior can differ from the Fabric mod and from newer Minecraft releases.

## Requirements

- Paper 1.20.6 or newer
- Java 21
- CommandAPI 11.0.0 or newer
- LuckPerms 5.5 or newer

CommandAPI and LuckPerms are required plugins. Install them alongside VanillaPermissionsPlugin.

## Gamemode example

These LuckPerms commands allow a group to set its own gamemode to survival or spectator and deny changes to other players:

```text
/lp creategroup limited-gamemode
/lp group limited-gamemode permission set minecraft.command.gamemode true
/lp group limited-gamemode permission set minecraft.command.gamemode.survival true
/lp group limited-gamemode permission set minecraft.command.gamemode.spectator true
/lp group limited-gamemode permission set minecraft.command.gamemode.creative false
/lp group limited-gamemode permission set minecraft.command.gamemode.adventure false
/lp group limited-gamemode permission set minecraft.command.gamemode.other false
/lp user <player> parent add limited-gamemode
```

The base node enables `/gamemode`; each mode node controls that mode. Explicit `false` nodes override inherited grants. The `.other` node blocks gamemode changes targeting anyone else. To grant changes to others, set `.other` to `true` and grant or deny each `minecraft.command.gamemode.<mode>.other` node as needed.

## Command permissions

The plugin replaces these vanilla commands with permission-checked Paper commands:

- `/gamemode`: base command, individual modes, other-player changes
- `/tp` and `/teleport`: entity and coordinate destinations, entity targets
- `/time`: set, add, query
- `/weather`: clear, rain, thunder
- `/gamerule`: query and set
- `/difficulty`
- `/effect`: give and clear
- `/whitelist`: add, remove, on, off

Each command requires its `minecraft.command.<name>` base node; action-specific nodes in `src/main/resources/plugin.yml` narrow access. Parent grants apply unless a matching node is explicitly denied. Operator players bypass plugin checks by default; set `default_permissions.ops_bypass_all: false` in `config.yml` to apply LuckPerms checks to operators too.

Selector restrictions apply when a replacement command parses a selector argument. Nodes are `minecraft.selector.self.s`, `minecraft.selector.player.a`, `.p`, `.r`, and `minecraft.selector.entity.e`. `/effect` also checks `minecraft.selector.player.effect.targets` or `minecraft.selector.entity.effect.targets` for selector targets. These checks do not intercept selectors in unrelated plugins' commands or every native Paper command.

## Other controls

- `minecraft.bypass.whitelist`, `minecraft.bypass.player-limit`, `minecraft.bypass.force-gamemode`, `minecraft.bypass.move-speed.player`
- `minecraft.operator_block.<block>.<place|view|break>` for command, jigsaw, and structure blocks
- `minecraft.debug_stick.use.block`

Enable or disable command, selector, bypass, and admin checks in `config.yml`:

```yaml
features:
  command_permissions: true
  selector_permissions: true
  bypass_permissions: true
  admin_permissions: true

default_permissions:
  ops_bypass_all: true
```

Plugin administration uses `/vanillapermissions reload`, `/vanillapermissions check <player>`, and `/vanillapermissions debug`.

## License

MIT. See [LICENSE](LICENSE).

# VanillaPermissionsPlugin

Paper port of [DrexHD's VanillaPermissions](https://github.com/DrexHD/VanillaPermissions). LuckPerms controls native vanilla command paths while Paper keeps command parsing and execution.

## Requirements

- Paper 1.20.6
- Java 21
- CommandAPI 11.0.0
- LuckPerms 5.5

The native command hook uses Paper and Minecraft internals tested against 1.20.6. Later versions are not verified.

## Command permissions

Each Brigadier node checks `minecraft.command.<full path>`. A LuckPerms `true` grants that node and `false` denies it. An undefined node uses the command's original vanilla permission requirement. Dotted parent nodes do not grant child nodes by themselves; use LuckPerms inheritance or wildcard permissions for broad grants.

Gamemode has separate mode nodes. For example, grant `minecraft.command.gamemode` and `minecraft.command.gamemode.survival` to allow `/gamemode survival`; leave `minecraft.command.gamemode.survival.target` unset to keep explicit target forms behind vanilla's requirement. Target forms do not yet distinguish self from other players.

The native `/execute` tree adds Drex-style `if permission`, `feedback`, and `oplevel` paths.

## Other controls

- `minecraft.bypass.whitelist`, `minecraft.bypass.player-limit`, `minecraft.bypass.force-gamemode`, `minecraft.bypass.move-speed.player`
- `minecraft.operator_block.<block>.<place|view|break>` for command, jigsaw, and structure blocks
- `minecraft.debug_stick.use.block`

`config.yml` toggles command, bypass, and admin permissions. Selector parsing and per-target selector permissions are not implemented yet.

Install CommandAPI and LuckPerms alongside the plugin. Plugin commands: `/vanillapermissions reload`, `/vanillapermissions check <player>`, `/vanillapermissions debug`.

## License

MIT. See [LICENSE](LICENSE).

# VanillaPermissionsPlugin

Paper plugin inspired by [DrexHD's VanillaPermissions](https://github.com/DrexHD/VanillaPermissions). LuckPerms controls granular vanilla command paths while Paper keeps native parsing and execution.

## Requirements

- Paper 1.20.6
- Java 21
- CommandAPI 11.0.0
- LuckPerms 5.5

Install plugin normally in `plugins/`; no server agent or startup flag required. Later Paper versions are unverified.

## Command permissions

Each Brigadier node checks `minecraft.command.<full path>`. LuckPerms `true` grants, `false` denies, and undefined uses vanilla's requirement. Parent nodes do not grant child nodes by themselves; use LuckPerms inheritance or wildcards for broad grants.

Gamemode has separate mode nodes, including `minecraft.command.gamemode.survival`. The native `/execute` tree adds `if permission`, `feedback`, and `oplevel` paths.

## Selectors

`minecraft.selector` controls selector syntax. `SelectorPolicy` provides self, player, and entity scopes plus count limits and weight metadata through nodes under `minecraft.selector.*`. Normal Paper runtime integration is limited to behavior reachable through the plugin API; later versions may need adapter updates.

## Other controls

- `minecraft.bypass.whitelist`, `minecraft.bypass.player-limit`, `minecraft.bypass.force-gamemode`, `minecraft.bypass.move-speed.player`
- `minecraft.operator_block.<block>.<place|view|break>` and `minecraft.debug_stick.use.block` through Paper listeners

Plugin commands: `/vanillapermissions check <player>` and `/vanillapermissions debug`.

## License

MIT. See [LICENSE](LICENSE).

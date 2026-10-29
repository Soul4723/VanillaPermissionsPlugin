# Recipes

Ready-made LuckPerms setups for the things server owners ask for most. Run the commands in the console, or in game as an operator. If a node or the `.*` line is new to you, read [How permissions work](how-it-works.md) first.

Each recipe sets nodes on a group. The examples use groups such as `staff` and a player called `Steve`; use your own names. Create the group and add players to it before you run a recipe:

```text
/lp creategroup staff
/lp user Steve parent add staff
```

These recipes cover the vanilla commands. If EssentialsX or another plugin replaces a command such as `/gamemode`, the recipe applies to `/minecraft:gamemode`. See [A command ignores my nodes](troubleshooting.md#a-command-ignores-my-nodes).

Recipes on this page:

- [Staff mode: survival and spectator](#staff-mode-survival-and-spectator)
- [Builders: creative, time, weather and the debug stick](#builders-creative-time-weather-and-the-debug-stick)
- [Moderators: kick, ban and teleport](#moderators-kick-ban-and-teleport)
- [Helpers: teleport to players only](#helpers-teleport-to-players-only)
- [VIPs: join a full server](#vips-join-a-full-server)
- [Staff: join during whitelist maintenance](#staff-join-during-whitelist-maintenance)
- [Event hosts: effects and items for up to 10 players](#event-hosts-effects-and-items-for-up-to-10-players)
- [Operators: no command blocks](#no-command-blocks), [no /op, /deop or /stop](#no-op-deop-or-stop), [no mobs or other entities as targets](#no-mobs-or-other-entities-as-targets)

## Staff mode: survival and spectator

For staff who switch to spectator to watch players, usually together with a vanish plugin.

```text
/lp group staff permission set minecraft.command.gamemode true
/lp group staff permission set minecraft.command.gamemode.* false
/lp group staff permission set minecraft.command.gamemode.survival true
/lp group staff permission set minecraft.command.gamemode.spectator true
```

The `.*` line closes every mode and every target. The next two lines open survival and spectator for the player themselves.

Players in `staff` can:

- switch themselves to spectator with `/gamemode spectator`, and back with `/gamemode survival`.

They can't:

- use creative or adventure mode;
- change another player's game mode.

## Builders: creative, time, weather and the debug stick

For a build team that needs creative mode and control over daylight and weather.

```text
/lp group builder permission set minecraft.command.gamemode true
/lp group builder permission set minecraft.command.gamemode.* false
/lp group builder permission set minecraft.command.gamemode.creative true
/lp group builder permission set minecraft.command.gamemode.survival true
/lp group builder permission set minecraft.command.time true
/lp group builder permission set minecraft.command.weather true
/lp group builder permission set minecraft.debugstick true
```

Players in `builder` can:

- switch themselves between creative and survival;
- use `/time` and `/weather`;
- use the debug stick in creative mode. This needs Paper or a Paper fork.

They can't:

- use spectator or adventure mode, or change another player's game mode;
- place or use command blocks. Those stay for operators.

To allow `/gamemode` only in one world, add the world context to the first line: `/lp group builder permission set minecraft.command.gamemode true world=creative`. Replace `creative` with your world's folder name.

## Moderators: kick, ban and teleport

For moderators who deal with problem players.

```text
/lp group moderator permission set minecraft.command.kick true
/lp group moderator permission set minecraft.command.ban true
/lp group moderator permission set minecraft.command.pardon true
/lp group moderator permission set minecraft.command.teleport true
```

Players in `moderator` can:

- `/kick`, `/ban` and `/pardon` players;
- teleport to a player with `/tp Alex`, and bring one player to another with `/tp Alex Steve`.

They can't:

- use `/op`, `/deop`, `/stop` or any other operator command you did not grant.

On 1.8.8 - 1.12.2, grant `minecraft.command.tp` instead of `minecraft.command.teleport`.

## Helpers: teleport to players only

For helpers who need to reach a player quickly, without teleporting anyone else.

```text
/lp group helper permission set minecraft.command.teleport true
/lp group helper permission set minecraft.command.teleport.targets false
/lp group helper permission set minecraft.command.teleport.location false
```

Players in `helper` can:

- teleport themselves to a player with `/tp Alex`.

They can't:

- teleport to coordinates;
- teleport other players.

On 1.8.8 - 1.12.2, grant `minecraft.command.tp` instead of `minecraft.command.teleport` in the first line.

## VIPs: join a full server

For ranks that may join when the server has reached its player limit.

```text
/lp group vip permission set minecraft.bypass.player-limit true
```

Players in `vip` can join when the server is full. Being an operator does not count, so add operators to `vip` too if they need this. Folia ignores this node and always refuses players over the limit.

## Staff: join during whitelist maintenance

For staff who must get in while the whitelist keeps everyone else out.

```text
/lp group staff permission set minecraft.bypass.whitelist true
```

Players in `staff` can join while the whitelist is on, without being on it. Operators can join anyway.

## Event hosts: effects and items for up to 10 players

For players who run events and hand out effects and items, but should not hit the whole server at once.

```text
/lp group host permission set minecraft.command.effect true
/lp group host permission set minecraft.command.give true
/lp group host permission set minecraft.selector true
/lp group host meta set minecraft.selector.limit.effect.targets 10
/lp group host meta set minecraft.selector.limit.give.targets 10
```

Players in `host` can:

- use `/effect` and `/give` on themselves and on other players;
- use selectors such as `@a` and `@p`, for up to 10 targets per command.

They can't:

- affect more than 10 players or mobs with one `/effect` or `/give`. They get `You can target at most 10 players or entities here.`

The two `meta` lines are limits, not permissions, so they use `meta set`. More about limits is in [Selector limits and weights](permissions.md#selector-limits-and-weights).

## Operators

Operators have every vanilla command, so these recipes take things away. Put your operators in an `admin` group, then deny what they should not do. A `false` node wins over operator status.

```text
/lp creategroup admin
/lp user Steve parent add admin
```

### No command blocks

```text
/lp group admin permission set minecraft.operator_block.command_block.* false
```

Operators in `admin` can't:

- place, open or break command blocks, including repeating and chain command blocks.

They can still:

- use every other command;
- create a command block with `/setblock`, `/fill` or `/clone`. Command blocks already in the world keep running.

The line above is enough for most servers. If operators must not create command blocks with commands either, also deny these three commands:

```text
/lp group admin permission set minecraft.command.setblock false
/lp group admin permission set minecraft.command.fill false
/lp group admin permission set minecraft.command.clone false
```

To take away structure blocks and jigsaws too, deny `minecraft.operator_block.*` instead of `minecraft.operator_block.command_block.*`.

### No /op, /deop or /stop

```text
/lp group admin permission set minecraft.command.op false
/lp group admin permission set minecraft.command.deop false
/lp group admin permission set minecraft.command.stop false
```

Operators in `admin` keep every other command, but can't op or de-op anyone and can't stop the server. The console still can.

### No mobs or other entities as targets

For operators who should not run `/kill @e` by accident.

```text
/lp group admin permission set minecraft.selector.entity.* false
```

Operators in `admin` can still target players, for example with `@a` or a name. Any command that would target a mob, an item or another non-player entity is refused, and nothing happens.

## Tested on

Every recipe on this page is tested on Paper 1.21.11 and Spigot 1.21.11 with LuckPerms. The staff, helper, moderator, VIP, whitelist and `/op` recipes are also tested on Paper 1.12.2. Older servers have fewer nodes; see [1.8.8 - 1.12.2](compatibility.md#188---1122).

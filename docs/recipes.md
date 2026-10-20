# Recipes

Copy-paste setups, written for LuckPerms. They assume a non-operator group called `member` unless noted. Node meanings are in [Permissions](permissions.md).

## Self-only survival mode

```text
/lp group member permission set minecraft.command.gamemode true
/lp group member permission set minecraft.command.gamemode.creative false
/lp group member permission set minecraft.command.gamemode.adventure false
/lp group member permission set minecraft.command.gamemode.spectator false
/lp group member permission set minecraft.selector true
/lp group member permission set minecraft.selector.player.gamemode.target.survival.target false
```

`/gamemode survival` and `/gamemode survival @s` work; `/gamemode survival Steve` and `@a` are refused.

## `/tp` to coordinates only

Not to or of other players:

```text
/lp group member permission set minecraft.command.tp true
/lp group member permission set minecraft.command.teleport.destination false
/lp group member permission set minecraft.command.teleport.targets false
```

## Limit `/effect` on other players

Moderators can apply `/effect` to other players, at most five at a time:

```text
/lp group moderator permission set minecraft.command.effect true
/lp group moderator permission set minecraft.selector true
/lp group moderator meta set minecraft.selector.limit.effect.targets.give.targets.effect 5
/lp group moderator meta set minecraft.selector.limit.effect.targets.give.targets.effect.seconds 5
/lp group moderator meta set minecraft.selector.limit.effect.targets.give.targets.effect.seconds.amplifier 5
/lp group moderator meta set minecraft.selector.limit.effect.targets.give.targets.effect.seconds.amplifier.hideParticles 5
/lp group moderator meta set minecraft.selector.limit.effect.targets.give.targets.effect.infinite 5
/lp group moderator meta set minecraft.selector.limit.effect.targets.give.targets.effect.infinite.amplifier 5
/lp group moderator meta set minecraft.selector.limit.effect.targets.give.targets.effect.infinite.amplifier.hideParticles 5
```

Meta has no wildcards, so every way of typing `/effect give` needs its own line: with or without `<seconds>` or `infinite`, `<amplifier>` and `<hideParticles>`. A form without a line has no limit.

## Debug stick for builders

Builders can use the debug stick in creative mode, except on oak trapdoors:

```text
/lp group builder permission set minecraft.debugstick true
/lp group builder permission set minecraft.debug_stick.use.minecraft.oak_trapdoor false
```

## Take operator blocks away from operators

```text
/lp group admin permission set minecraft.operator_block.* false
```

## Allow only `/time`

Deny every vanilla command except `/time` (plugin commands are not affected):

```text
/lp group member permission set minecraft.command.* false
/lp group member permission set minecraft.command.time true
/lp group member permission set minecraft.command.time.* true
```

`minecraft.command.time.*` does not match the root `minecraft.command.time`, so grant the root separately. The `.*` grant is needed because `minecraft.command.* false` also covers every sub-command of `/time`. See [Wildcards](permissions.md#wildcards). `minecraft.command.* false` also matches Paper's own `minecraft.command.selector`, so set `minecraft.selector true` if the group should keep `@` selectors.

## With another permissions plugin

Every recipe is just a list of `node value` pairs, so translate each line to your plugin's syntax. With a plugin that uses a `permissions:` list per group (GroupManager, for example), the survival recipe becomes:

```yaml
permissions:
  - minecraft.command.gamemode
  - '-minecraft.command.gamemode.creative'
  - '-minecraft.command.gamemode.adventure'
  - '-minecraft.command.gamemode.spectator'
  - minecraft.selector
  - '-minecraft.selector.player.gamemode.target.survival.target'
```

A leading `-` means `false` in that format; check your plugin's documentation. Limits and weights (`minecraft.selector.limit.…`) need Vault chat info instead of a node; see [Permission plugins](providers.md). Wildcards depend on the plugin.

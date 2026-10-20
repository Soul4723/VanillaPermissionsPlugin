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

`/gamemode survival` and `/gamemode survival @s` work; `/gamemode survival Steve` is refused, and `@a` is refused while anyone else is online (the check only refuses targets other than the caller). Checked on Paper 1.21.11 with LuckPerms; `@a` is accepted when the caller is the only player online.

The selector node (`…gamemode.target.survival.target`) is shaped differently from the command node (`minecraft.command.gamemode.survival.target`) because the selector path starts with the command and the argument being checked (`gamemode.target`) and then lists the rest of what was typed (`survival.target`), the layout upstream uses, while the command node follows the command tree (mode first, then target).

## `/tp` to coordinates only

Not to or of other players:

```text
/lp group member permission set minecraft.command.tp true
/lp group member permission set minecraft.command.teleport.destination false
/lp group member permission set minecraft.command.teleport.targets false
```

Checked on Paper 1.21.11 with LuckPerms, as a non-operator:

- `/tp 100 64 100` works. Its node is `minecraft.command.teleport.location`, which the recipe leaves unset (allowed).
- `/tp <player>`, `/tp @s <player>`, `/tp Bob 1 64 1` and `/tp @a 1 64 1` are refused.
- `/teleport` is not available at all: only the `tp` root is granted, and `minecraft.command.teleport` stays unset (operators only).

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

Meta has no wildcards, so every way of typing `/effect give` needs its own line: with or without `<seconds>` or `infinite`, `<amplifier>` and `<hideParticles>`. A form without a line has no limit. To find the key for a form you want to limit, run `/lp verbose on <player>`, have the player type that form with a selector, then `/lp verbose off`: the output has a line such as `meta: minecraft.selector.limit.effect.targets.give.targets.effect.seconds.amplifier - null`, and that key is what `meta set` takes. Checked on Paper 1.21.11.

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

`minecraft.command.time.*` does not match the root `minecraft.command.time`, so grant the root separately. The `.*` grant is needed because `minecraft.command.* false` also covers every sub-command of `/time`. See [Wildcards](permissions.md#wildcards).

### What `minecraft.command.* false` also turns off

On Paper 1.21.11 with LuckPerms, a non-operator in a group with `minecraft.command.* false` lost `/me`, `/msg`, `/tell`, `/w`, `/teammsg`, `/tm`, `/trigger` and `/minecraft:help` (all answered "Unknown or incomplete command"), because Paper gives these to everyone through nodes that the wildcard also matches. The wildcard also matches Paper's `minecraft.command.selector`, so `@` selectors stopped working. Bukkit's own `/help` was not affected. To keep them, add these lines (checked: each command works again, with arguments):

```text
/lp group member permission set minecraft.command.me true
/lp group member permission set minecraft.command.me.* true
/lp group member permission set minecraft.command.msg true
/lp group member permission set minecraft.command.msg.* true
/lp group member permission set minecraft.command.tell true
/lp group member permission set minecraft.command.w true
/lp group member permission set minecraft.command.teammsg true
/lp group member permission set minecraft.command.teammsg.* true
/lp group member permission set minecraft.command.tm true
/lp group member permission set minecraft.command.trigger true
/lp group member permission set minecraft.command.trigger.* true
/lp group member permission set minecraft.command.help true
/lp group member permission set minecraft.selector true
```

`/tell`, `/w` and `/tm` are redirects with their own root node but use `/msg`'s and `/teammsg`'s argument nodes, so they need no `.*` line of their own. Leave out `minecraft.selector true` if the group should not use `@` selectors.

## With another permissions plugin

Every recipe is just a list of `node value` pairs, so translate each line to your plugin's syntax. Limits and weights (`minecraft.selector.limit.…`) need Vault chat info instead of a node; see [Permissions plugins](providers.md). Wildcards depend on the plugin; see the GroupManager notes below.

### GroupManager

GroupManager 3.2 with Vault 1.7.3, checked on Paper 1.20.6 and 1.21.11. Its `groups.yml` has a top-level `groups:` key, then one entry per group with `default`, a `permissions` list, `inheritance` and `info` (the structure of the `groups.yml` that GroupManager generates on first start). In `permissions`, `node` grants, `-node` negates (the node is `false`) and `+node` is an exception to a broader negation.

The file is per world: `plugins/GroupManager/worlds/<world>/groups.yml` (`worlds/world/groups.yml` for the default world). The self-only survival recipe becomes a new group:

```yaml
groups:
  Member:
    default: false
    permissions:
    - minecraft.command.gamemode
    - -minecraft.command.gamemode.creative
    - -minecraft.command.gamemode.adventure
    - -minecraft.command.gamemode.spectator
    - minecraft.selector
    - -minecraft.selector.player.gamemode.target.survival.target
    inheritance: []
    info:
      build: true
```

Add the group to the existing `groups:` list, save the file, run `manload` (console, or `/manload` in game) to reload it, then put a player in it with `manuadd <player> Member`. What was seen on both versions, with a non-operator in `Member`:

- `/gamemode survival` works on both versions, and `/gamemode survival @s` and `@a` (alone on the server) work on 1.21.11.
- `/gamemode creative`, `adventure` and `spectator` are refused.
- `/gamemode survival <other player>` and `/gamemode survival @a` (with another player online) answer `You do not have permission to target other players here`.
- `/time set day` is refused (`/time` was not granted).

How GroupManager's nodes behave with this plugin (Paper 1.21.11, checked with `/vanillapermissions check`):

- `-node` shows as `false`. It only affects its own node, not the broader one: with `minecraft.command.time`, `minecraft.command.time.query` and `-minecraft.command.time.set` in one group, `/time query daytime` works, `/time set day` is refused, and `/time add 1` (unset) follows vanilla.
- `+minecraft.command.time.query` shows as `true`.
- **Wildcards do nothing here.** `minecraft.command.*` and `minecraft.command.time.*` show their sub-nodes as unset, so list nodes explicitly. A `-minecraft.command.*` in the group also made an explicit `minecraft.command.time` in the same group show as unset (GroupManager's own `manucheckp` reported the negation for it). The "Allow only `/time`" recipe above therefore does not translate; non-operators already lack vanilla commands whose nodes are unset, so grant only what the group needs.
- Selector limits and weights were not tested with GroupManager.

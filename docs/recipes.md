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

`/gamemode survival` and `/gamemode survival @s` work; `/gamemode survival Steve` is refused, and `@a` is refused while anyone else is online (the check only refuses targets other than the caller), so it works when the caller is the only player online.

The selector node (`…gamemode.target.survival.target`) is shaped differently from the command node (`minecraft.command.gamemode.survival.target`) because the selector path starts with the command and the argument being checked (`gamemode.target`) and then lists the rest of what was typed (`survival.target`), the layout upstream uses, while the command node follows the command tree (mode first, then target).

## `/tp` to coordinates only

Not to or of other players:

```text
/lp group member permission set minecraft.command.tp true
/lp group member permission set minecraft.command.teleport.destination false
/lp group member permission set minecraft.command.teleport.targets false
```

As a non-operator:

- `/tp 100 64 100` works. Its node is `minecraft.command.teleport.location`, which the recipe leaves unset (allowed).
- `/tp <player>`, `/tp @s <player>`, `/tp Bob 1 64 1` and `/tp @a 1 64 1` are refused.
- `/teleport` is not available at all: only the `tp` root is granted, and `minecraft.command.teleport` stays unset (operators only).

## Limit `/effect` on other players

Moderators can apply `/effect` to other players, at most five at a time:

```text
/lp group moderator permission set minecraft.command.effect true
/lp group moderator permission set minecraft.selector true
/lp group moderator meta set minecraft.selector.limit.effect.targets 5
```

Limit and weight meta have no wildcards, but the plugin looks for the most specific key first and then drops the last part of the path until it finds one that is set. `minecraft.selector.limit.effect.targets` is the shortest key, so it caps every way of typing `/effect` (give and clear, with or without `<seconds>`, `<amplifier>` and `<hideParticles>`). To cap only `/effect give`, set `minecraft.selector.limit.effect.targets.give` instead. A longer key set on the same group overrides a shorter one for its own form, so you can allow more targets for one form. Weights work the same way: each player's weight is the most specific `minecraft.selector.weight…` key set for them along the same path. Keys written in upstream's full form still work.

To find the key for a form you want to limit, run `/lp verbose on <player> & minecraft.selector`, have the player type that form with a selector, then `/lp verbose off`: the output has a line such as `meta: minecraft.selector.limit.effect.targets.give.targets.effect.seconds.amplifier - null`, and you can set that key or any shorter one that starts the same way.

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

## Take every vanilla command away from staff

Operators have every vanilla command. To take them all away from a group of operators (plugin commands are not affected), deny the wildcard and grant back only what the group needs, here `/time`:

```text
/lp group admin permission set minecraft.command.* false
/lp group admin permission set minecraft.command.time true
/lp group admin permission set minecraft.command.time.* true
```

`minecraft.command.time.*` does not match the root `minecraft.command.time`, so grant the root separately. The `.*` grant is needed here only because `minecraft.command.* false` also covers every sub-command of `/time`. See [Wildcards](permissions.md#wildcards).

You do not need this for ordinary players. A non-operator has no vanilla operator command until you grant one, and `minecraft.command.time true` alone is enough for `/time`.

### What `minecraft.command.* false` also turns off

A non-operator in a group with `minecraft.command.* false` loses `/me`, `/msg`, `/tell`, `/w`, `/teammsg`, `/tm`, `/trigger` and `/minecraft:help` (they answer "Unknown or incomplete command"), because Paper gives these to everyone through nodes that the wildcard also matches. The wildcard also matches Paper's `minecraft.command.selector`, so `@` selectors stop working. Bukkit's own `/help` is not affected. To keep them, add these lines:

```text
/lp group admin permission set minecraft.command.{me,msg,tell,w,teammsg,tm,trigger,help} true
/lp group admin permission set minecraft.command.{me,msg,teammsg,trigger}.* true
/lp group admin permission set minecraft.selector true
```

The `{a,b}` groups are LuckPerms shorthand, which works while its `apply-shorthand` option is on (the default). With another permissions plugin, or if you turned it off, write one line per node: the first line is `me`, `msg`, `tell`, `w`, `teammsg`, `tm`, `trigger` and `help` as `minecraft.command.<name> true`, and the second is `minecraft.command.<name>.* true` for `me`, `msg`, `teammsg` and `trigger`.

`/tell`, `/w` and `/tm` are redirects with their own root node but use `/msg`'s and `/teammsg`'s argument nodes, so they need no `.*` line of their own. Leave out `minecraft.selector true` if the group should not use `@` selectors.

## With another permissions plugin

Every recipe is just a list of `node value` pairs, so translate each line to your plugin's syntax. Limits and weights (`minecraft.selector.limit.…`) need Vault chat info instead of a node; see [Permissions plugins](providers.md). Wildcards depend on the plugin; see the GroupManager notes below.

### GroupManager

GroupManager 3.2 with Vault 1.7.3. Its `groups.yml` has a top-level `groups:` key, then one entry per group with `default`, a `permissions` list, `inheritance` and `info` (the structure of the `groups.yml` that GroupManager generates on first start). In `permissions`, `node` grants, `-node` negates (the node is `false`) and `+node` is an exception to a broader negation.

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

Add the group to the existing `groups:` list, save the file, run `manload` (console, or `/manload` in game) to reload it, then put a player in it with `manuadd <player> Member`. A non-operator in `Member` can use `/gamemode survival` and `/gamemode survival @s`, is refused the other game modes and other players, and cannot use `/time` (not granted).

How GroupManager's nodes behave with this plugin:

- `-node` shows as `false` and only affects its own node, not the broader one: with `minecraft.command.time`, `minecraft.command.time.query` and `-minecraft.command.time.set` in one group, `/time query daytime` works, `/time set day` is refused, and `/time add 1` (unset) follows vanilla.
- `+minecraft.command.time.query` shows as `true`.
- **Wildcards do nothing here.** `minecraft.command.*` and `minecraft.command.time.*` show their sub-nodes as unset, so list nodes explicitly. A `-minecraft.command.*` in the group also makes an explicit `minecraft.command.time` in the same group show as unset. The "Take every vanilla command away from staff" recipe above therefore does not translate; non-operators already lack vanilla commands whose nodes are unset, so grant only what the group needs.
- Selector limits and weights depend on Vault chat info and were not tested with GroupManager.

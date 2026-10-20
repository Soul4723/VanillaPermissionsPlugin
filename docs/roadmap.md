# Roadmap

The long-term scope is any Minecraft version or server software where a permissions plugin alone does not give granular control over vanilla behaviour. Phase 1 is the 1.0.0 release: Paper 1.20.6 through 26.3 (see [Compatibility](compatibility.md)). Later phases branch out. Nothing below is promised or scheduled.

Status: **planned** (intended, approach known), **research** (feasibility unknown), **not planned**.

| Target | Status | What it would take |
| --- | --- | --- |
| Folia | planned | Declare `folia-supported`. Commands run on region threads, and some vanilla commands are removed on Folia, so the tree patching and every place the plugin touches world state need an audit. The most plausible next target. |
| Purpur, Pufferfish | research | Paper forks, expected to work. Verify by hand, then add them to the boot matrix in `compat.yml`. |
| Paper 1.19 – 1.20.4 | research | Spigot-mapped runtime and Java 17. There is no lifecycle `COMMANDS` event, so the command tree must be reached another way, with per-version name tables or modules as CommandAPI does. |
| Spigot | research | The same work as old Paper, plus no Paper API at all. |
| Pre-1.13 (for example 1.8.9) | research | No Brigadier. CraftBukkit already gives `minecraft.command.<name>` for each vanilla command, but nothing finer (game modes, selectors, `/execute`). Needs a separate adapter that hooks the command wrapper. |
| Fabric, Quilt | not planned | [DrexHD/VanillaPermissions](https://github.com/DrexHD/VanillaPermissions) already covers these. If a gap appears, decide between contributing upstream and forking. |
| NeoForge, Forge | research | NeoForge's PermissionAPI has no "unset" for dynamic nodes, so a module would call LuckPerms directly or use a fabric-permissions-api-style provider. |
| Sponge | research | Check whether Sponge's native permissions already give granular vanilla control before building anything. |

## Project layout

Supporting a second platform would move the build to a Gradle core plus one module per platform, as spark and Geyser do. That is adopted when the second platform starts, not before. See [Multi-platform layout](development/releasing.md#multi-platform-layout-later).

## Pending verification

Player-level checks on Paper 26.3 are pending a test client; see [Compatibility](compatibility.md#how-each-level-was-verified).

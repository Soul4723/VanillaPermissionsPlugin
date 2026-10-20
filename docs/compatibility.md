# Compatibility

One jar, `VanillaPermissionsPlugin-<version>.jar`, covers Paper **1.20.6 through 26.3**.

## Supported versions

| Minecraft (Paper) | Java | Verified |
| --- | --- | --- |
| 1.20.6 | 21 | Boot and install in CI; full live behaviour checks |
| 1.21, 1.21.1, 1.21.3, 1.21.4, 1.21.6, 1.21.7, 1.21.8, 1.21.10 | 21 | Boot and install in CI |
| 1.21.11 | 21 | Boot and install in CI; full live behaviour checks |
| 1.21.5, 1.21.9 | 21 | Boot and install in CI. Paper only published experimental builds. |
| 26.1.1 | 25 | Boot and install in CI. Paper only published an experimental build. |
| 26.1.2, 26.2 | 25 | Boot and install in CI |
| 26.3 | 25 | Boot and install in CI; startup and console checks only, because no test client exists yet |

## How each level was verified

- **Boot and install:** the [Compatibility workflow](../.github/workflows/compat.yml) downloads a pinned Paper build for each version, starts it with the plugin and LuckPerms 5.5, and checks that the plugin installs its permission checks (`scripts/compat-smoke.sh`). It runs on every push and pull request.
- **Full live behaviour checks:** commands, selectors, bypasses, operator blocks and the debug stick were exercised on live servers on 1.20.6 and 1.21.11.
- **Startup and console only (26.3):** the plugin was checked at startup and on the console. Player-level checks are pending a test client for 26.3.
- **Experimental builds:** Paper published only ALPHA builds for 1.21.5, 1.21.9 and 26.1.1. They are tested, but Paper itself marks them experimental.

The plugin logs a warning once at startup on a Minecraft version outside the tested list. Support is decided by feature detection when the plugin installs, so an untested version may work; if the command tree cannot be patched, the plugin logs why, restores the vanilla commands and disables itself.

## Java

| Minecraft | Java |
| --- | --- |
| 1.20.6 – 1.21.11 | 21 |
| 26.x | 25 |

The jar targets Java 21 bytecode; the Java version is whatever your Paper version requires.

## Other server software

| Software | Status |
| --- | --- |
| Paper | Supported (range above) |
| Purpur, Pufferfish | Paper forks. Expected to work, untested |
| Folia | Not supported |
| Spigot, CraftBukkit | Not supported |
| Paper 1.20.5 and older | Not supported |

Paper 1.20.5 lacks Paper's Brigadier lifecycle API; the plugin refuses to load there with "Unsupported API version". Older Paper versions and other server types are discussed in the [Roadmap](roadmap.md).

## Permissions plugins

See [Permission plugins](providers.md). LuckPerms 5.5 is what CI runs with.

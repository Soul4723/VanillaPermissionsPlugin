# AGENTS.md

Guidance for coding agents and contributors. Humans: see [CONTRIBUTING.md](CONTRIBUTING.md); this file supplements it.

## Project overview

VanillaPermissionsPlugin is a Bukkit-family plugin (Paper, Folia, Spigot, Purpur, Pufferfish; Minecraft 1.8.8 - 26.3) that adds permission nodes to vanilla commands, selectors and operator features. It is a port of DrexHD's VanillaPermissions. One jar serves every supported server: the adapter is chosen at startup by probing the server. Version is in the root `pom.xml`; the Maven group is `io.github.soul4723`, the Java package `io.github.soul4723.vanillapermissions`.

## Layout

| Module | Java | Role |
| --- | --- | --- |
| `core` | 8 | Plugin main, permission providers (LuckPerms, Bukkit, Vault), selector rules, `Adapters`, `/vanillapermissions` |
| `brigadier` | 8 | Brigadier tree patching shared by the 1.13+ adapters |
| `paper` | 21 | Paper and Folia 1.20.6+ and forks |
| `spigot` | 8 | Reflective CraftBukkit adapter: Spigot 1.13+, Paper 1.13 - 1.20.5 |
| `legacy` | 8 | 1.8.8 - 1.12.2: typed-command checks and grammars |
| `plugin` | - | `plugin.yml` and the shaded jar `target/VanillaPermissionsPlugin-<version>.jar` |

Other paths: `docs/` (user docs; `docs/development/` for contributors), `scripts/compat-smoke.sh` and `scripts/live-test/` (server boot and live tests), `.github/workflows/` (`build.yml` boot matrix, `release.yml`). Start with [docs/development/architecture.md](docs/development/architecture.md).

## Build and test

```text
mvn verify                      # build, all tests, shaded jar (JDK 21 + Maven)
mvn -pl <module> -am test       # one module and what it depends on, e.g. mvn -pl spigot -am test
```

Unit tests run on JDK 21 for every module. Server-specific paths cannot be unit-tested; the boot matrix in `build.yml` covers them, and live tests are described in [docs/development/testing.md](docs/development/testing.md). The two `SEVERE: Could not use LuckPerms` lines in test output are expected.

## Code style

- Java 8 modules compile with `--release 8`: no `List.of`, `var`, `String.isBlank` and so on. Only `paper` may use Java 21 features.
- `core` compiles against the Spigot 1.8.8 API; keep Paper-only and 1.13+ calls out of it (use reflection in an adapter).
- Reach server internals by reflection, not by compiling against NMS. A server version never decides the adapter; probes do.
- Permission queries can run on worker threads, so providers must be thread-safe.
- Indentation is set in `.editorconfig`: 4 spaces in Java and XML, 2 in YAML. Match the surrounding naming and comment density; comments explain why, not what changed.

## Pull requests and changelog

- Branch from `main`, one change per pull request, imperative commit subjects with the reason in the body.
- Add or update tests. `mvn verify` must pass; the CI boot matrix runs on every pull request.
- Add a user-facing line under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md) ([Keep a Changelog](https://keepachangelog.com/en/1.1.0/) sections: Added, Changed, Fixed, Security). Releases are cut by the maintainer; see [docs/development/releasing.md](docs/development/releasing.md).
- When you change a node, command, version range or limit, update the matching page in `docs/` in the same pull request. `SupportedVersions` must stay in step with the boot matrix.
- Do not push, tag or edit GitHub state (issues, releases, labels) unless asked.

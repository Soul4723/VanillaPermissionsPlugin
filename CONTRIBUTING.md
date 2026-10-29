# Contributing

Thanks for helping out. By contributing you agree that your changes are licensed under the project's [MIT license](LICENSE).

## Issues

Use the [issue templates](https://github.com/Soul4723/VanillaPermissionsPlugin/issues/new/choose). A bug report needs the plugin, server software and permissions plugin versions and the output of `/vanillapermissions debug` (and of `/vanillapermissions check` for the node involved). For security problems see [SECURITY.md](SECURITY.md). For a larger change, open an issue first so the approach can be agreed before you write code.

## Building

You need JDK 21 and Maven (JDK 21 builds every module, including the Java 8 ones).

```text
mvn verify                  # compile, run all tests, write target/VanillaPermissionsPlugin-<version>.jar
mvn -pl core -am test       # one module's tests (core, brigadier, paper, spigot, legacy, plugin)
```

Copy the jar to a Paper or Spigot server's `plugins` folder together with a permissions plugin such as LuckPerms to try it. Boot smoke tests and live tests need server JDKs and are described in [Testing](docs/development/testing.md). The code layout is in [Architecture](docs/development/architecture.md).

## Pull requests

- Branch from `main` and open the pull request against `main`. Keep one change per pull request.
- Follow the style of the surrounding code; `.editorconfig` sets indentation (4 spaces in Java and XML, 2 in YAML). Java 8 modules must not use APIs newer than Java 8.
- Add or update tests. CI runs `mvn verify` and a boot matrix of real servers on every pull request. Say which server software and version you tested on.
- Add a line under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md) for anything users will notice.
- Write commit messages in the imperative mood ("Check selector permissions after redirects"), with the reason in the body if it is not obvious.

Releases are cut by the maintainer; see [Releasing](docs/development/releasing.md).

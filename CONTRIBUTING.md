# Contributing

Thanks for helping out. By contributing you agree that your changes are licensed under the project's [MIT license](LICENSE).

## Reporting problems and ideas

Use the [issue templates](https://github.com/Soul4723/VanillaPermissionsPlugin/issues/new/choose). A bug report needs the plugin, server software and LuckPerms versions and the output of `/vanillapermissions check`. For anything security-related see [SECURITY.md](SECURITY.md) instead.

For a larger change, open an issue first so the approach can be agreed before you write code.

## Building

You need JDK 21 and Maven.

```
mvn verify
```

This compiles, runs the tests and writes the jar to `target/VanillaPermissionsPlugin-<version>.jar`. Copy it to a Paper server's `plugins` folder together with LuckPerms to try it. Only Paper 1.20.6 is tested so far; say which version you used when you open a pull request.

## Pull requests

- Branch from `main` and open the pull request against `main`.
- Keep one change per pull request, and follow the style of the code around it. `.editorconfig` covers indentation (4 spaces in Java and XML, 2 in YAML).
- Add or update tests. CI runs `mvn verify` on every pull request.
- Add a line under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md) for anything users will notice.
- Write commit messages in the imperative mood ("Check selector permissions after redirects"), with the reason in the body if it is not obvious.

Releases are cut by the maintainer; see [docs/development/releasing.md](docs/development/releasing.md).

# Security policy

## Supported versions

Only the latest release receives security fixes.

## Verifying a download

Releases are published only from [GitHub Releases](https://github.com/Soul4723/VanillaPermissionsPlugin/releases) and are immutable: once published, their files cannot be replaced. Check a jar with the `.sha256` file next to it (`sha256sum -c VanillaPermissionsPlugin-<version>.jar.sha256`), or with GitHub's signed release attestation: `gh release verify-asset v<version> VanillaPermissionsPlugin-<version>.jar -R Soul4723/VanillaPermissionsPlugin`.

## Reporting a vulnerability

Please do not open a public issue for a security problem, for example a way to run a command or reach a target without the permission that should be required.

Report it privately through GitHub: open the repository's **Security** tab and choose **Report a vulnerability**, or go to <https://github.com/Soul4723/VanillaPermissionsPlugin/security/advisories/new>.

Include the plugin version, server software and version, permissions plugin and version, the permissions involved, and the steps to reproduce.

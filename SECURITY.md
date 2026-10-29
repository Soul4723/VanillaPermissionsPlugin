# Security policy

## Supported versions

Only the latest release gets security fixes.

## Verifying a download

Download the plugin only from [GitHub Releases](https://github.com/Soul4723/VanillaPermissionsPlugin/releases). Published releases cannot be changed afterwards. Each jar comes with a `.sha256` file, so you can check that the jar is the one that was released.

- **Linux or macOS:** put both files in one folder and run `sha256sum -c VanillaPermissionsPlugin-<version>.jar.sha256`. It should print `OK`.
- **Windows:** run `certutil -hashfile VanillaPermissionsPlugin-<version>.jar SHA256` in Command Prompt. The result must match the text in the `.sha256` file.
- **GitHub CLI:** releases also carry a signed attestation. Run `gh release verify-asset v<version> VanillaPermissionsPlugin-<version>.jar -R Soul4723/VanillaPermissionsPlugin`.

## Reporting a vulnerability

Do not open a public issue for a security problem. An example is a way to run a command, or to target a player, without the permission that should be needed.

Report it privately instead: open the repository's **Security** tab and choose **Report a vulnerability**, or go to <https://github.com/Soul4723/VanillaPermissionsPlugin/security/advisories/new>.

Please include:

- the plugin version;
- the server software and version;
- the permissions plugin and its version;
- the permissions involved;
- the steps to reproduce.

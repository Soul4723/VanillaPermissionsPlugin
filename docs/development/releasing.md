# Releasing and maintenance

Maintainer notes. Contributor-facing rules are in [CONTRIBUTING.md](../../CONTRIBUTING.md).

## Versioning

- **Semantic Versioning**, `MAJOR.MINOR.PATCH`, set once in `pom.xml` (`<version>`). Pre-releases use a hyphen: `1.1.0-rc.1`.
- **Git tag** is `v` + the pom version: `v1.0.0`. The tag is the only thing that triggers a release.
- **One jar covers the whole supported range**, so its name has no Minecraft version: `VanillaPermissionsPlugin-1.0.0.jar`. The adapter is chosen at startup (see [Architecture](architecture.md#release-artifacts)). Split the jar only for a concrete incompatibility, such as a loader or bytecode conflict, and keep one source tree, one version and one tag if that happens.
- **The supported range is documented, not encoded**: `api-version` in `plugin.yml`, the version tables in `docs/compatibility.md` and `SupportedVersions`, and a line in the changelog whenever it changes.
- Bump MAJOR when a supported server software or version is dropped or a node changes meaning, MINOR for new nodes and features, PATCH for fixes.

## Cutting a release

1. Open a release PR: move the entries under `## [Unreleased]` in `CHANGELOG.md` to a new `## [X.Y.Z] - YYYY-MM-DD` section, and update the compare links at the bottom of the file.
2. In that PR, run `mvn versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false`; it updates the root version and every module's parent reference.
3. Merge the release PR, then wait for both **Build** and **Compatibility** on that merge commit on `main` to pass.
4. Tag that merge commit: `git tag -a vX.Y.Z <merge-sha> -m "VanillaPermissionsPlugin X.Y.Z"`, then `git push origin vX.Y.Z`.
5. Run the same command with the next development version (`1.2.0-SNAPSHOT` after `1.1.0`; development builds are `1.1.0-SNAPSHOT` until then).

This release-then-SNAPSHOT sequence follows [ViaVersion](https://github.com/ViaVersion/ViaVersion/blob/master/gradle.properties) (`projectVersion`) and [EssentialsX](https://github.com/EssentialsX/Essentials/blob/2.x/build.gradle) (`version`). The tag/pom check rejects a release tag on a SNAPSHOT commit.

The **Release** workflow (`.github/workflows/release.yml`) then:

- fails if the tag does not equal `v` + the pom version;
- runs `mvn verify`;
- writes `VanillaPermissionsPlugin-X.Y.Z.jar.sha256`;
- takes the matching `CHANGELOG.md` section as the release notes (it falls back to GitHub's generated notes if the section is missing), as DrexHD/VanillaPermissions does (`fetchChangelog()` in `build.gradle.kts`);
- creates a GitHub Release with the jar and checksum, marked pre-release when the version contains a hyphen.

Running the workflow by hand (`workflow_dispatch`) only builds and checks; it never publishes.

## CI

- **Build** (`build.yml`): runs on pushes to `main`, pull requests and manual dispatch; superseded PR runs are cancelled; the jar is uploaded as the `VanillaPermissionsPlugin` artifact. It uses one JDK, 21, which compiles both the Java 8 modules (`--release 8`) and the Java 21 `paper` module; see [Architecture](architecture.md#java-targets).
- **Compatibility** (`compat.yml`): runs on main pushes, every PR, manual dispatch and twice-weekly scheduled runs. It builds the compat jar and boots the supported software/version matrix. Spigot jars are built with pinned BuildTools and cached, never uploaded. The `compat` aggregate check follows [setuptools’ `check` job](https://github.com/pypa/setuptools/blob/main/.github/workflows/main.yml) using alls-green. PRs restore download caches; non-PR runs save them (Spigot jar builds also save on PR misses for downstream jobs).
- If you make a check "required" in branch protection, do not add `paths` or `paths-ignore` to Build or Compatibility (`build` and `compat` required checks); a skipped required check blocks merging.
- Dependabot (`.github/dependabot.yml`) updates Maven dependencies and GitHub Actions weekly, grouped, with a seven-day cooldown. Paper API, Spigot API and Brigadier are ignored on purpose: they define compatibility floors. Name-only ignores also suppress security-update PRs for those dependencies ([Dependabot docs](https://docs.github.com/en/code-security/dependabot/working-with-dependabot/dependabot-options-reference#ignore)); these are compile-time APIs, not shaded dependencies. Paper’s API SNAPSHOT can change, so rebuilding an old tag is not fully reproducible.

### Compatibility boot tests

`compat.yml` and `scripts/compat-smoke.sh` boot real servers. The setup follows other projects that do the same:

- [josemmo/yamipa `tests.yml`](https://github.com/josemmo/yamipa/blob/master/.github/workflows/tests.yml): a matrix of `spigot`, `paper`, `purpur` and `folia`, a Java version chosen per Minecraft version, assertions on the plugin's own log lines, `fail-fast: false`. The checks grep only the plugin's lines, because Purpur and Spigot log exceptions of their own.
- [ez-plugins/EzShops `smoke-tests.yml`](https://github.com/ez-plugins/EzShops/blob/main/.github/workflows/smoke-tests.yml): a `User-Agent` on Fill requests, the newest STABLE build with a fallback to any channel, and the server log uploaded with `if: always()`.
- [PaperMC docs: downloads service](https://github.com/PaperMC/docs/blob/main/src/content/docs/misc/downloads-service.mdx): Fill asks for a non-generic `User-Agent` with a contact URL; `compat-smoke.sh` sends one on every request.
- [jpenilla/run-task](https://github.com/jpenilla/run-task) (`DownloadsAPIServiceImpl.kt`): verify the SHA-256 from the API, download to a temp file, move it into place, cache jars per version and build (`fetch_verified`, the `actions/cache` key).
- [itzg/docker-minecraft-server](https://github.com/itzg/docker-minecraft-server) (`scripts/start-deployPufferfish`, `start-deployPurpur`): the Pufferfish Jenkins job and the Purpur API as download sources. Builds are pinned with SHA-256 here because Pufferfish publishes no checksums.

## Branching

Trunk only: all work goes to `main`, and releases are tags on `main`, as in LuckPerms, spark, ViaVersion and EssentialsX. If a fix for an older major is ever needed, branch `N.x` from the last `vN.x.y` tag, fix there, tag `vN.x.(y+1)` from that branch and cherry-pick forward (EssentialsX does this with `2.x`).

**Support policy.** `docs/compatibility.md` says which versions are tested and which are only expected to work. Old versions are dropped by a MAJOR bump.

# Releasing

How versions, tags and the release workflow fit together. Contributor rules are in [CONTRIBUTING.md](../../CONTRIBUTING.md).

## Versioning

- [Semantic Versioning](https://semver.org/), set once in the root `pom.xml`. Development builds are `X.Y.Z-SNAPSHOT`; pre-releases use a hyphen, such as `1.2.0-rc.1`.
- Bump MAJOR when a supported server software or version is dropped or a node changes meaning, MINOR for new nodes and features, PATCH for fixes.
- The tag is `v` + the version, such as `v1.1.0`. Pushing it is what publishes a release.
- One jar covers every supported server, so its name has no Minecraft version: `VanillaPermissionsPlugin-1.1.0.jar`. The adapter is chosen at startup (see [Architecture](architecture.md#release-artifacts)).
- The supported range is stated in `plugin.yml` (`api-version`), `SupportedVersions`, [Compatibility](../compatibility.md) and the changelog whenever it changes.

## Cutting a release

The version is changed by a commit, not by the workflow, so the tagged source is exactly what the release jar is built from. The steps below use `1.1.0`, released on 2026-11-01, after `1.0.0`.

1. On a branch from `main`, set the version and the timestamp written into the jar:

   ```bash
   mvn versions:set -DnewVersion=1.1.0 -DgenerateBackupPoms=false
   mvn versions:set-property -Dproperty=project.build.outputTimestamp \
     -DnewVersion=2026-11-01T00:00:00Z -DgenerateBackupPoms=false
   ```

2. In `CHANGELOG.md`, rename `## [Unreleased]` to `## [1.1.0] - 2026-11-01`, add a new empty `## [Unreleased]` above it, and update the links at the bottom:

   ```markdown
   [Unreleased]: https://github.com/Soul4723/VanillaPermissionsPlugin/compare/v1.1.0...HEAD
   [1.1.0]: https://github.com/Soul4723/VanillaPermissionsPlugin/compare/v1.0.0...v1.1.0
   ```

3. Optional dry run: in **Actions → Release → Run workflow**, pick the branch. It builds the jar, writes the checksum and extracts the release notes into the `release-files` artifact without publishing anything.
4. Open a pull request, merge it, and wait for the **Build** run on the merge commit on `main` to succeed.
5. Tag that commit and push the tag:

   ```bash
   git tag -a v1.1.0 <merge-commit> -m "VanillaPermissionsPlugin 1.1.0"
   git push origin v1.1.0
   ```

6. Set the next development version and merge it through a pull request:

   ```bash
   mvn versions:set -DnewVersion=1.2.0-SNAPSHOT -DgenerateBackupPoms=false
   ```

### What the Release workflow does

On a `v*` tag, `.github/workflows/release.yml`:

1. runs `mvn verify` and fails if the tag is not `v` + the pom version;
2. fails unless **Build** succeeded on `main` for the tagged commit. If the tag was pushed too early, re-run the workflow once Build has passed;
3. writes `VanillaPermissionsPlugin-1.1.0.jar.sha256`;
4. takes the `## [1.1.0]` section of `CHANGELOG.md` as the release notes, with relative links made absolute. A missing section fails the release; only pre-releases fall back to GitHub's generated notes;
5. creates the GitHub Release with the jar and the checksum file, marked as a pre-release when the version contains a hyphen.

Anyone can check a download with `sha256sum -c VanillaPermissionsPlugin-1.1.0.jar.sha256`. Building the same tag again with the same JDK gives the same jar, unless Paper's API snapshot, which the build compiles against, has changed since.

## CI

- **Build** (`.github/workflows/build.yml`) runs on every pull request, on pushes to `main` that change more than Markdown files, issue templates or the live-test harness, by hand, and twice a week so its caches stay warm.
  - The `build` job runs `mvn verify` with JDK 21, which compiles the Java 8 modules with `--release 8` and the Java 21 `paper` module (see [Architecture](architecture.md#java-targets)). The jar is uploaded as an artifact.
  - The `smoke` matrix boots every supported server with that jar and LuckPerms through `scripts/compat-smoke.sh`. Its rows must match `SupportedVersions`; `SupportedVersionsTest` fails otherwise. Server builds are pinned; Spigot rows build their server with a pinned BuildTools and keep it in the Actions cache, never as an artifact.
  - The `compat` job fails if the build or any matrix row failed. Make `compat` the required check in branch protection.
- **Dependabot** proposes Maven and GitHub Actions updates monthly, grouped, after a seven-day cooldown. Paper API, Spigot API and Brigadier are ignored because they set the supported floors; ignoring them by name also suppresses security updates for them, which is acceptable for compile-only APIs.

## Branching and support

All work goes to `main`, and releases are tags on `main`. If an older major ever needs a fix, branch `N.x` from its last tag, fix and tag there, and cherry-pick the fix forward.

Only the latest release receives fixes. [Compatibility](../compatibility.md) lists which versions are tested and which are only expected to work; dropping a version is a MAJOR change.

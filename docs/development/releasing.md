# Releasing and long-term maintenance

Maintainer notes. Contributor-facing rules are in [CONTRIBUTING.md](../../CONTRIBUTING.md).

## Versioning

- **Semantic Versioning**, `MAJOR.MINOR.PATCH`, set once in `pom.xml` (`<version>`). Pre-releases use a hyphen: `1.1.0-rc.1`.
- **Git tag** is `v` + the pom version: `v1.0.0`. The tag is the only thing that triggers a release.
- **The jar name has no Minecraft version**: `VanillaPermissionsPlugin-1.0.0.jar`. One jar covers the whole supported Paper range, as with ViaVersion (`ViaVersion-5.12.1.jar`), EssentialsX (`EssentialsX-2.22.0.jar`) and CommandAPI (`CommandAPI-12.1.0-Paper.jar`). Projects that put the Minecraft version in the version (`0.164.0+26.3` for Fabric API, `0.3.6+26.1.2` for DrexHD/VanillaPermissions) do so because they ship a different jar per Minecraft version, which this plugin does not.
- **The supported range is documented, not encoded**: `api-version` in `plugin.yml`, the "tested on" line in the README, and a line in the changelog whenever it changes (the pattern of Geyser's and ViaVersion's README "Supported Versions" sections).
- Bump MAJOR when a Paper version is dropped or a node changes meaning, MINOR for new nodes and features, PATCH for fixes.

## Cutting a release

1. Move the entries under `## [Unreleased]` in `CHANGELOG.md` to a new `## [X.Y.Z] - YYYY-MM-DD` section, and update the compare links at the bottom of the file.
2. Set `<version>` in `pom.xml` to `X.Y.Z` (commit both changes to `main`).
3. Wait for the **Build** workflow on `main` to pass.
4. Tag and push: `git tag vX.Y.Z && git push origin vX.Y.Z`.

The **Release** workflow (`.github/workflows/release.yml`) then:

- fails if the tag does not equal `v` + the pom version;
- runs `mvn verify`;
- writes `VanillaPermissionsPlugin-X.Y.Z.jar.sha256`;
- takes the matching `CHANGELOG.md` section as the release notes (it falls back to GitHub's generated notes if the section is missing);
- creates a GitHub Release with the jar and checksum, marked pre-release when the version contains a hyphen.

Running the workflow by hand (`workflow_dispatch`) only builds and checks; it never publishes.

Notes come from the changelog, as in DrexHD/VanillaPermissions (`fetchChangelog()` in `build.gradle.kts`). Fabric API instead generates them in CI with `FabricMC/fabric-action-scripts` (`context: changelog`). A hand-kept changelog is the right size here.

## CI

- **Build** (`build.yml`): runs on pushes to `main`, pull requests and manual dispatch; superseded PR runs are cancelled; the jar is uploaded as the `VanillaPermissionsPlugin` artifact. It uses one JDK (21, the `--release` target). None of the surveyed projects runs a JDK matrix.
- Other workflows (such as a Paper boot matrix) should keep the same style: a lowercase file name, a short `name:`, `permissions: contents: read`, and a per-ref `concurrency` group.
- If you make a check "required" in branch protection, do not add `paths-ignore` to it; a skipped required check blocks merging. Geyser uses `paths-ignore`, but this build takes about a minute.
- Dependabot (`.github/dependabot.yml`) updates Maven dependencies and GitHub Actions weekly, grouped, with a seven-day cooldown. Paper API is ignored on purpose: it defines the supported-version floor.

## Branching model

**Now: trunk only.** All work goes to `main`; releases are tags on `main`. This is what LuckPerms (`master`), spark (`master`), ViaVersion (`master`) and EssentialsX (`2.x`) do for their current line.

**Later: add branches only when forced.** Choose by what forces the split:

| Situation | Do this | Precedent |
|---|---|---|
| A fix for an older plugin major | Branch `N.x` from the last `vN.x.y` tag, fix there, tag `vN.x.(y+1)` from that branch; cherry-pick forward to `main`. | EssentialsX develops on `2.x` and has tagged older lines (`stable-2.13.1`). |
| One jar can no longer cover the Paper range (for example a NMS or internals break) | Prefer one source tree that builds a jar per Minecraft version (Stonecutter, as DrexHD/VanillaPermissions does). Version the artifacts `X.Y.Z+<mc>`. | DrexHD/VanillaPermissions moved from the branches `1.19.2` ... `1.21.5` to a single `main` plus `versions/<mc>`; its tags are `0.3.5+1.20.1`, `0.3.6+26.1.2`. |
| You must maintain old and new Minecraft sources separately | Branch per Minecraft version, named exactly by it (`1.21.1`); new work lands on the newest and is cherry-picked back. | Fabric API (`26.3`, `26.4`, ... with maintainers cherry-picking, per its CONTRIBUTING "Targeted Minecraft versions") and Carpet (`1.21.11`, `26.1`, ...). |

Recommendation: never start with per-Minecraft branches. Fabric API and Carpet need them because mods are compiled against one exact Minecraft version; a Paper plugin built against the oldest supported Paper API is not. DrexHD/VanillaPermissions still has the old `1.19.2` ... `1.21.5` branches, but current releases build from `main` with Stonecutter.

**Support policy.** Say in the README which Paper versions are tested and which are only expected to work, and drop old versions by a MAJOR bump. Fabric API's rule ("the latest stable version and the latest snapshot are always supported; older ones as far as feasible") is a workable template.

## Multi-platform layout (later)

Not needed while this is a single Paper plugin on Maven. Adopt it when a second platform (Velocity, Fabric, Sponge) is actually planned, not before.

When it is time, move to **Gradle with a shared core and one module per platform**, as spark and Geyser do:

```
core/           (spark: spark-common, Geyser: core) platform-independent permission logic
paper/          (spark: spark-paper, Geyser: bootstrap/spigot) the current plugin
fabric/         (spark: spark-fabric, Geyser: bootstrap/mod/fabric)
velocity/       ...
```

- `settings.gradle(.kts)` includes the modules; Geyser maps directories with `project(":spigot").projectDir = file("bootstrap/spigot")`.
- One `ci.yml` builds everything with `./gradlew build` and uploads every platform jar in one artifact, like spark's `jars` or LuckPerms' `jars`.
- Name jars `<Project>-<Platform>-<version>.jar` (LuckPerms: `LuckPerms-Bukkit-*.jar`; Geyser: `Geyser-Spigot.jar`; CommandAPI: `CommandAPI-12.1.0-Paper.jar`). Keep one shared version for all platforms (CommandAPI, LuckPerms).
- Keep a single tag and a single GitHub Release for all platform jars (CommandAPI attaches Paper, Spigot and Velocity jars to one release).
- Gradle is justified only by the multi-module need. A single-module Maven build stays simpler.

## Publishing to Modrinth and Hangar (later)

The project publishes to GitHub Releases only. When that changes, leave the tag, the changelog and the checksum as they are and add steps after the release job:

- **Gradle projects**: `me.modmuss50.mod-publish-plugin` with `publishMods { modrinth {...} }`, as in DrexHD/VanillaPermissions and Fabric API; the changelog comes from the same changelog section. For Hangar, ViaVersion calls `publishAllPublicationsToHangar` and `modrinth` from `publish.yml` (secrets `HANGAR_TOKEN`, `MODRINTH_TOKEN`).
- **Maven**: call the Modrinth and Hangar REST APIs from a workflow step, or move to Gradle.
- Keep publishing in the same job or a dependent job, and run it only on tags. Mark pre-release tags as such (Carpet maps GitHub's pre-release flag to a beta channel on Modrinth/CurseForge).
- Declare the supported game versions in the upload metadata (`minecraftVersions`), kept in one property so the README and the upload cannot disagree (ViaVersion: `mcVersions` and `mcVersionRange` in `gradle.properties`).

## Sources

- DrexHD/VanillaPermissions: `build.gradle.kts`, `stonecutter.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `.github/workflows/{build,release}.yml`, `CHANGELOG.md`, branch list, releases.
- gnembon/fabric-carpet: `.github/workflows/{build-on-pr,devbuild,publish-release}.yml`, `gradle.properties`, branch list, releases.
- lucko/spark: `.github/workflows/ci.yml`, `settings.gradle`, tags, `README.md`.
- GeyserMC/Geyser: `.github/workflows/{build,pull-request}.yml`, `settings.gradle.kts`, `README.md`, `CONTRIBUTING.md`.
- LuckPerms/LuckPerms: `.github/workflows/ci.yml`, `build.gradle`, `CONTRIBUTING.md`, tags.
- EssentialsX/Essentials: `.github/workflows/{build-master,build-pr}.yml`, `.github/ISSUE_TEMPLATE/`, `.github/PULL_REQUEST_TEMPLATE/`, `CONTRIBUTING.md`, releases.
- FabricMC/fabric: `.github/workflows/{build,release}.yml`, `CONTRIBUTING.md` ("Targeted Minecraft versions"), branch list, releases.
- ViaVersion/ViaVersion (and ViaBackwards): `.github/workflows/{build,publish}.yml`, `.github/dependabot.yml`, `.github/ISSUE_TEMPLATE/`, `SECURITY.md`, `.editorconfig`, `gradle.properties`, `README.md`.
- JorelAli/CommandAPI (CommandAPI/CommandAPI): `.github/workflows/build.yml`, `.github/dependabot.yml`, `SECURITY.md`, `CONTRIBUTING.md`, releases.
- walterhiggins/ScriptCraft: `README.md`, branch list, last commit (2020-02-04): a Bukkit/Spigot/CanaryMod-era plugin that supports "Java 7 or later" and was never ported on. Lesson: state a support policy, so that an unmaintained version range does not linger in the docs.
- [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/), [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html).
- All repository data was read with `gh api repos/<owner>/<repo>/contents/<path>`, `/branches`, `/releases` and `/tags` on 2026-10-20.

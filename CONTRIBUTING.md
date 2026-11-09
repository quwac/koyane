# Contributing to Koyane

Run these commands from the repository root.

The [README](README.md) covers supported runtimes, dependencies, and a minimal application.
The [cheat sheet](docs/CHEATSHEET.md) covers APIs and their behavior.
The [Workers example](examples/hello-workers/README.md) covers local execution and deployment.
The [Lambda Web Functions example](examples/hello-lambda-web/README.md) covers Node.js execution and AWS packaging.

## Set up

```sh
mise trust
mise install --locked rumdl java bun node hk pkl lychee gh zizmor betterleaks \
    github:github/gh-actions-lock github:jdx/jactionlint
mise run setup
```

`mise run setup` installs the root and example Bun dependencies with frozen lockfiles, then installs the hk Git hooks.

| Tool | Used for |
| --- | --- |
| Eclipse Temurin 17 | Gradle, Kotlin compiler, and detekt |
| Bun | Repository scripts and dependency installation |
| rumdl | Markdown linting and formatting |
| Node.js | Wrangler and Node HTTP adapter integration tests |
| hk | Formatting, linting, links, workflow checks, and pre-commit hooks |
| Gradle wrapper | Kotlin builds and tests |

`mise.toml` and `mise.lock` pin the toolchain.
Run commands through `mise run` or from a mise-activated shell.
Without activation, run quality commands as `mise exec -- hk check --all`.

## Checks

```sh
hk check --all
mise run test
```

| Command | Checks |
| --- | --- |
| `hk check --all` | Kotlin and JavaScript formatting/linting, Markdown linting and links, dependency use, secrets, and workflow files |
| `mise run test` | Kotlin tests, compiler fixtures, BOM consumption, and Workers and Node HTTP integration tests |
| `mise run test:compile` | Compiler fixtures only |
| `mise run test:bom` | Isolated Maven publication and versionless Kotlin/JS and Kotlin/Wasm consumers |
| `mise run test:workers:wasm` | Kotlin/Wasm Worker integration test |
| `mise run test:workers:js` | Kotlin/JS Worker integration test |
| `mise run test:node` | Kotlin/JS and Kotlin/Wasm Node HTTP integration tests |
| `mise run deploy:dry-run:wasm` | Kotlin/Wasm Worker bundle without upload |
| `mise run deploy:dry-run:js` | Kotlin/JS Worker bundle without upload |

`mise run test` runs its tasks in sequence because the repository does not run concurrent Gradle builds in one checkout.
The Worker tests build a production bundle, apply the example migrations to isolated local D1 state,
then make HTTP requests through workerd.

Use `hk fix --all` for supported formatting fixes.
It does not stage files.
The pre-commit hook checks staged content and does not apply fixes.

`hk check --all --profile ci` also checks external Markdown links.
Normal local link checks are offline.

## IntelliJ IDEA

Open the repository root as a Gradle project and use the checked-in wrapper.
Set the Gradle JVM to the JDK reported by `mise where java`.

Gradle uses strict dependency verification.
IDE import downloads source artifacts as well as build artifacts, so source checksums must be present in `gradle/verification-metadata.xml`.
After changing a Gradle dependency, reload the project and fix every reported verification entry before committing.

## Maven publications

The `koyane-bom` Java platform manages the common version of every public `koyane-*` library module.
Library modules publish their Kotlin Multiplatform, JavaScript, and Wasm artifacts with Gradle's `maven-publish` plugin.
Examples and compiler fixtures are not published.

Publish the current snapshot to your local Maven repository:

```sh
mise exec -- ./gradlew publishToMavenLocal
```

The coordinates are `dev.koyane:<module>:0.0.1-SNAPSHOT`.
Consumers import `platform("dev.koyane:koyane-bom:0.0.1-SNAPSHOT")` and omit versions from module dependencies.
See the [dependency examples](README.md#dependencies) for Workers and Node.js.

`mise run test:bom` publishes to an isolated repository under `build/reports/bom-tests`.
It checks BOM coverage and POM packaging, compiles versionless JS/Wasm consumers,
and verifies that importing the BOM does not pull optional modules into a minimal HTTP application.
External dependencies retain strict verification in the consumer builds.
No remote publishing repository, credentials, or release workflow is configured.

## Dependency updates

Keep version declarations, lockfiles, and Gradle verification metadata together.
Review every checksum and lockfile change.

Update mise tools with:

```sh
mise lock --platform macos-arm64,macos-x64,linux-x64,linux-arm64
mise install --locked rumdl java bun node hk pkl lychee gh zizmor betterleaks \
    github:github/gh-actions-lock github:jdx/jactionlint
hk check --all
mise run test
```

The root `bun.lock` covers repository tools.
`examples/hello-workers/bun.lock` covers Wrangler and example scripts.
Update either lockfile through Bun; do not edit it by hand.

When adding or removing Kotlin/JS or Kotlin/Wasm modules, refresh the Gradle-managed workspace lockfiles:

```sh
./gradlew kotlinUpgradePackageLock kotlinWasmUpgradePackageLock
```

For an intentional Gradle dependency update, generate missing verification entries,
review them against the publishing repository, then rerun the normal checks:

```sh
./gradlew --write-verification-metadata sha256 detekt allTests \
    :examples:hello-workers:wasmJsProductionExecutableCompileSync
./gradlew --write-verification-metadata sha256 \
    :compile-tests:compileKotlinWasmJs -PcompileFixture=Positive
hk check --all
mise run test
```

Do not regenerate verification metadata during an ordinary build.

## GitHub Actions

Workflow actions use full commit SHAs.
After changing an action dependency, regenerate and verify the lockfile:

```sh
gh-actions-lock --no-narrow
gh-actions-lock --verify
```

Do not edit `.github/workflows/actions.lock` by hand.
`hk check --all` verifies local lock coverage, jactionlint, and zizmor.

CI runs quality checks, Kotlin tests, compiler fixtures, and Workers tests in separate jobs.
It also builds both deployment bundles without uploading them.

## Cloudflare credentials

`mise.local.toml` is ignored by Git and may set `CLOUDFLARE_ACCOUNT_ID` for the example deployment.
Start from `mise.local.toml.example`.
Keep account IDs, subdomains, OAuth credentials, and database IDs out of tracked files.
Wrangler stores its login outside the repository.

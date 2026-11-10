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
| `mise run test` | Kotlin tests, compiler fixtures, BOM consumption, Workers, Node, and HTTP function integration tests (Docker required) |
| `mise run test:compile` | Compiler fixtures only |
| `mise run test:bom` | Isolated Maven publication and versionless Kotlin/JS and Kotlin/Wasm consumers |
| `mise run test:release` | Release guards and tag-only pushes against temporary local Git repositories |
| `mise run test:workers:wasm` | Kotlin/Wasm Worker integration test |
| `mise run test:workers:js` | Kotlin/JS Worker integration test |
| `mise run test:node` | Kotlin/JS and Kotlin/Wasm Node HTTP integration tests |
| `mise run test:functions:node` | JS/Wasm Vercel Fetch contract, Lambda HTTP events, and official Functions Framework |
| `mise run test:functions:edge` | JS/Wasm Supabase Edge Runtime user workers (Docker required) |
| `mise run deploy:dry-run:wasm` | Kotlin/Wasm Worker bundle without upload |
| `mise run deploy:dry-run:js` | Kotlin/JS Worker bundle without upload |

`mise run test` runs its tasks in sequence because the repository does not run concurrent Gradle builds in one checkout.
The Worker tests build a production bundle, apply the example migrations to isolated local D1 state,
then make HTTP requests through workerd.
Function examples contain their own packaging and deployment instructions:
[Vercel and Supabase](examples/hello-fetch-functions/README.md),
[Cloud Run functions](examples/hello-cloud-run-functions/README.md), and
[Lambda Event Functions](examples/hello-lambda/README.md).
Supabase tests start a pinned Edge Runtime container, exercise user workers, and remove the container on completion.
These tests do not deploy to cloud services.

Use `hk fix --all` for supported formatting fixes.
It does not stage files.
The pre-commit hook checks staged content and does not apply fixes.

Detekt requires KDoc on public classes, interfaces, objects, functions, properties, and enum entries
in library and example production sources.
The `UndocumentedPublicClass`, `UndocumentedPublicFunction`, and `UndocumentedPublicProperty` rules
fail `./gradlew detekt`, the hk Kotlin check, and CI when documentation is missing.
Overridden functions and properties are exempt from these rules; test sources follow Detekt's default test exclusions.
Document runtime validation, defaults, and failure behavior alongside the API they describe.

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
The Vanniktech Maven Publish plugin publishes Kotlin Multiplatform, JavaScript, and Wasm artifacts,
including sources and Javadoc archives, for the explicit public-module list in `build.gradle.kts`.
Examples and compiler fixtures are not published.

Publish the current snapshot to your local Maven repository:

```sh
mise exec -- ./gradlew publishToMavenLocal
```

The coordinates are `dev.koyane:<module>:0.0.1-SNAPSHOT`.
Consumers import `platform("dev.koyane:koyane-bom:0.0.1-SNAPSHOT")` and omit versions from module dependencies.
See [Dependencies](README.md#dependencies) for BOM setup
and [Supported services](README.md#supported-services) for adapter selection.

`mise run test:bom` copies Git-selected sources and publishes to an isolated repository under `build/reports/bom-tests`.
The source copy keeps publication versions and generated npm workspace files out of the main build's test workspace.
It checks BOM coverage and POM packaging, compiles versionless JS/Wasm consumers,
and verifies that importing the BOM does not pull optional modules into a minimal HTTP application.
External dependencies retain strict verification in the consumer builds.
The `version` Gradle property overrides the default `0.0.1-SNAPSHOT` across all publications.
To verify a release version locally without uploading it:

```sh
ORG_GRADLE_PROJECT_version=0.1.0 mise run test:bom
```

## Releases

The Publish workflow accepts `vX.Y.Z` tags with no leading zeros or prerelease suffixes.
It verifies that the tagged commit belongs to `main`, then calls the Verify workflow.
Tests use the locked snapshot workspace; isolated BOM publication and consumption use the tag's version.
Quality checks, compiler fixtures, library tests, BOM consumption, host integration tests, and Workers deployment dry runs
must all succeed before a separate job signs and publishes the libraries and BOM to Maven Central.
GitHub Release creation runs after publication with its own `contents: write` permission.
The verification jobs do not receive publishing secrets.

The Central Portal account must have publishing access to `dev.koyane`.
If the namespace is unregistered, verify ownership of `koyane.dev` using the
[Sonatype namespace procedure](https://central.sonatype.org/register/namespace/).
Distribute the signing key's public key as described in the
[Central signing requirements](https://central.sonatype.org/publish/requirements/gpg/).
Configure these repository secrets before releasing:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central Portal publishing token username |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal publishing token password |
| `SIGNING_KEY` | ASCII-armored private signing key |
| `SIGNING_PASSWORD` | Signing key passphrase |

Create a release from a clean worktree whose `HEAD` exactly matches `origin/main`:

```sh
mise run release 0.1.0
```

The command checks the version, fetches `main` and tags, rejects an existing version tag,
and requires the main Verify workflow to have succeeded for that exact commit.
It creates and pushes an annotated tag only; builds and publishing run in CI.
The worktree may remain on a `feature/*` or `hotfix/*` branch.

Central publication and artifact download availability are separate stages;
[propagation can take time](https://vanniktech.github.io/gradle-maven-publish-plugin/central/#publishing-releases).
Confirm that the released BOM and target artifacts can be downloaded from Maven Central before announcing availability.
If publication succeeded and only GitHub Release creation failed, rerun that job without republishing the same version.

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

The root `bun.lock` covers repository tools and the Functions Framework integration-test dependency.
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

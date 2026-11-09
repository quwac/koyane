# Working on Koyane

## Branches and documentation

- Use ASCII branch names beginning with `feature/` or `hotfix/`.
  Rename a nonconforming working branch before making changes.
- The README addresses library users: compatibility, API usage, and current scope.
  Put contributor tooling and maintenance instructions in `CONTRIBUTING.md`,
  and example execution and deployment instructions in the example's README.
- Explanatory documents, designs, and specifications describe the current behavior
  and its guarantees. Do not include implementation history or a chronology of changes.

## Design constraints

- Small runtime, strong types, portable applications.
- Core provides primitives. Plugins provide opinions.
- Prefer compile-time safety, explicit Kotlin types and functions, and manual dependencies.
- Keep Core independent of HTTP, host APIs, and runtime implementations.
- Implement protocols through the public plugin API. HTTP middleware belongs in HTTP.
- Keep application code independent of the runtime adapter.
- Install HTTP explicitly with `install(Http) { ... }`. Register routes as strings
  with named `:id` or `{id}` segments inside its configuration. Handlers use a
  receiver context and explicitly extract HTTP values as Kotlin types. Handlers
  return `HttpResponse`; sealed results can use exhaustive `when` expressions.
- Avoid RouteN, positional handler parameters, DTO/property binding, reflection,
  application annotations, KSP, compiler plugins, and code generation. JSON codecs
  are explicit, typed dependencies; HTTP and Core do not depend on a JSON library.
- Validate external request data at runtime. Do not claim that the compiler validates
  HTTP status semantics, external input, or plugin installation presence.
- Avoid runtime reflection, implicit DI, stringly typed contracts, unnecessary
  generation, and abstractions without a concrete extension requirement.

## Scope and modules

Core, HTTP, authentication, CORS, error handling, and Logging provide
runtime-independent APIs targeting Kotlin `js` and `wasmJs`.
`koyane-workers` provides the Cloudflare Workers Fetch adapter. JVM, WASI, Native,
GraphQL, gRPC, OpenAPI, client generation, and compiler plugins are outside this
version's scope. The Workers adapter is independent of databases and drivers.
The example constructs Kagiroi's D1 driver from its typed host environment in
the target source sets and passes it to common application code.
`koyane-node` serves HTTP through Node.js on both targets. The Lambda Web Functions
example owns AWS listen-address configuration and deployment packaging.

- `koyane-core`: typed plugin configuration, installation, and extension lookup.
- `koyane-bom`: Maven BOM and Gradle platform aligning all public library module versions without adding runtime dependencies.
- `koyane-http`: HTTP messages, named routes, typed input extraction, response builders, and handlers.
- `koyane-bearer-authentication`: optional HTTP Bearer authentication through the public Core and HTTP APIs.
- `koyane-cors`: optional CORS policy, preflight handling, and response sharing through the public HTTP API.
- `koyane-error-handling`: optional exception and HTTP failure conversion through the public HTTP lifecycle.
- `koyane-logging`: optional HTTP lifecycle logging through the public Core and HTTP APIs.
- `koyane-workers`: Wasm/JavaScript and Fetch API adapter.
- `koyane-node`: Node.js HTTP server with buffered text requests and responses.
- `examples/hello-workers`: example application, Workers packaging, and integration tests.
- `examples/hello-lambda-web`: database-independent Node.js application and Lambda Web Functions packaging.
- `compile-tests`: positive and deliberately invalid Kotlin/Wasm compiler fixtures.

JDK runs Gradle, the Kotlin compiler, and detekt. It is a build requirement. The
Kotlin/JS Worker contains JavaScript; the Kotlin/Wasm Worker contains JavaScript
and Wasm. Bun runs repository scripts and dependency installation. Node.js runs
Wrangler; Gradle manages the Node.js runners for Kotlin/JS and Kotlin/Wasm tests.

## Setup and checks

```sh
mise trust
mise install --locked rumdl java bun node hk pkl lychee gh zizmor betterleaks \
    github:github/gh-actions-lock github:jdx/jactionlint
mise run setup
```

Run quality checks in a mise-activated shell. Without activation, prefix a command
with `mise exec --`.

```sh
hk check --all
hk check --all --profile ci
mise run test
mise run test:node
mise run deploy:dry-run:wasm
mise run deploy:dry-run:js
```

`hk fix --all` applies supported formatting fixes. Pre-commit checks staged code
without automatically fixing or staging it. Do not add mise tasks for linting or
formatting; those checks belong in `hk.pkl`.

Run checks appropriate to the change. Changes to response contracts, typed paths,
or plugin/input types require the actual compiler fixtures (`mise run test:compile`),
including expected diagnostic checks for negative cases. Changes to the Workers
bridge or Kotlin compiler version require local workerd integration tests and a
deployment dry run. Local checks do not establish successful remote deployment.

Keep versions, lockfiles, and SHA-256 verification metadata consistent. Review
dependency checksum changes; normal builds must verify dependencies without
regenerating the expected hashes. The update procedure is in `CONTRIBUTING.md`.

After changing workflow or action dependencies, regenerate
`.github/workflows/actions.lock` with `gh-actions-lock --no-narrow` and run
`gh-actions-lock --verify`. Never edit the lockfile by hand. Keep workflow `uses`
references pinned to full commit SHAs with version comments. hk runs jactionlint,
zizmor, and offline lockfile coverage checks; its `ci` profile adds online lockfile
verification. Normal checks must not regenerate lockfiles.

hk runs Betterleaks on the selected text files with secret values redacted.
Pre-commit checks the staged file contents; `hk check --all` checks the working
tree files selected by Git. Use synthetic credentials in temporary repositories
to verify secret detection. Do not add real credentials to tests or reports.

Cloudflare account IDs belong in the Git-ignored `mise.local.toml`; the committed
`mise.local.toml.example` contains placeholders only. Keep account subdomains and
authentication credentials out of tracked files. Wrangler manages OAuth login
credentials outside this repository. Both Wrangler configurations may declare the
example's named D1 binding and migration directory, but must not contain an
account-specific database ID.

Keep the dependency update cooldown at three days: mise range resolution, new
Bun resolutions in both package roots, and Dependabot updates for Actions,
Gradle, and Bun. jactionlint and zizmor validate the Dependabot policy. Existing
fixed versions and lockfiles remain valid; explicit version changes require
review because mise's age filter does not reject exact pins.

# Cloudflare Workers example

This example builds the same Koyane application for Kotlin/JS and Kotlin/Wasm.
It includes named routes, typed inputs, JSON, CORS, Bearer authentication, and D1 CRUD through Kagiroi.

The application is in [Application.kt](src/commonMain/kotlin/dev/quwac/koyane/example/Application.kt).
Its `main` function calls `defineD1Worker`, so the application setup remains in `commonMain`.

Run commands from the repository root.

## Set up

```sh
mise trust
mise install --locked java bun node
mise run deps
```

Gradle compiles Kotlin.
Bun installs the root and example dependencies.
Wrangler runs through Node.js.

For repository checks and Git hooks, follow the [contributing guide](../../CONTRIBUTING.md).

## Run locally

```sh
mise run dev:wasm
mise run dev:js
```

Each task builds its target, applies the committed migrations to local D1 state, and starts workerd.

```sh
curl http://localhost:8787/hello/Kotlin
# Hello, Kotlin!

curl -i http://localhost:8787/users/1
# 200, Ada

curl -i http://localhost:8787/users/not-a-long
# 400

curl -i http://localhost:8787/numbers/3 \
  -H 'Content-Type: application/json' -d '4'
# 201, 7

curl -i http://localhost:8787/json/echo \
  -H 'Content-Type: application/json' -d '["hello","世界"]'
# 200, ["hello","世界"]
```

The example installs `ContentNegotiation` and registers JSON with `json()`.
Every `receive` and `respond` call supplies an explicit serializer.

## D1 and Kagiroi

`koyane-workers-d1` reads the `DB` Worker binding and creates a Kagiroi `D1Database`.
The example receives it as `SqlDatabase` and keeps repository code in common Kotlin.
Kagiroi is a published dependency; its source modules are not part of this repository.

The [`migrations`](migrations) directory contains the local D1 schema.
It seeds user `1` as `Ada` for the `/users/1` route.

```sh
curl -i http://localhost:8787/todos \
  -H 'Content-Type: application/json' \
  -d '{"id":"123e4567-e89b-12d3-a456-426614174000","title":"Read D1 docs"}'
# 201

curl -i http://localhost:8787/todos
# 200

curl -i -X DELETE http://localhost:8787/todos/123e4567-e89b-12d3-a456-426614174000
# 204
```

## CORS and Bearer authentication

The example allows `https://client.example` in its CORS policy.
`/me` is protected by a `BearerAuthentication` guard.
The default verifier rejects all tokens.

```sh
curl -i -X OPTIONS http://localhost:8787/me \
  -H 'Origin: https://client.example' \
  -H 'Access-Control-Request-Method: GET' \
  -H 'Access-Control-Request-Headers: Authorization'
# 204

curl -i http://localhost:8787/me
# 401, WWW-Authenticate: Bearer realm="example"
```

## Deploy

Create a local Cloudflare account configuration:

```sh
cp mise.local.toml.example mise.local.toml
```

Set `CLOUDFLARE_ACCOUNT_ID` in `mise.local.toml`.
The file is ignored by Git.
Wrangler keeps OAuth credentials outside the repository.

```sh
mise trust mise.local.toml
mise run workers:login
mise run deploy:wasm
# or
mise run deploy:js
```

Both Worker configurations bind `DB` to the `koyane-hello` D1 database.
Apply the remote schema after the database exists:

```sh
mise run db:migrate:remote
```

Use a dry run to build without uploading:

```sh
mise run deploy:dry-run:wasm
mise run deploy:dry-run:js
```

See the [cheat sheet](../../docs/CHEATSHEET.md) for routing, inputs, responses, plugins, and testing.

# Vercel and Supabase Edge Functions

A database-independent common Kotlin application served through `koyane-fetch`.
JS and Wasm expose the same native `fetch(Request): Promise<Response>` function.
Run commands from the repository root with the toolchain in [CONTRIBUTING.md](../../CONTRIBUTING.md).

```sh
mise run build:functions:js
mise run build:functions:wasm
mise run test:functions:node
mise run test:functions:edge
```

The Node checks invoke Vercel's standard Fetch entry contract locally.
The Edge checks require Docker and run a Supabase Edge Runtime user worker on a loopback port,
with the image version and digest pinned in `scripts/test-functions.mjs`.
Tests cover routing, raw UTF-8 and JSON bodies, cookies, errors, and concurrent suspend handlers.
These checks do not establish successful cloud deployment.

## Vercel

`build/vercel-js` and `build/vercel-wasm` are deployable project directories.
Each contains `api/index.mjs`, a `package.json` selecting Node.js 24, `vercel.json`, and the compiled Kotlin modules.
The entry exports `default { fetch }`; rewrites send all paths to that function.
`includeFiles` retains the Kotlin Wasm files in the Node.js function bundle.
This example targets Vercel's Node.js runtime.

With the Vercel CLI installed and authenticated, deploy one package:

```sh
cd examples/hello-fetch-functions/build/vercel-js
vercel deploy
```

Use `vercel-wasm` to deploy the Wasm variant.
See [Vercel's Node.js runtime contract](https://vercel.com/docs/functions/runtimes/node-js).

## Supabase

`build/supabase-js` and `build/supabase-wasm` contain Supabase projects with a function named `hello`.
The TypeScript boundary calls `Deno.serve`, strips the `/hello` function prefix, and passes the request to Kotlin.
For example, `/functions/v1/hello/hello/World` reaches the application's `/hello/World` route.
The Wasm bridge reads the compiled module through `Deno.readFile`; `static_files` includes the Wasm bytes in the bundle.
The configuration keeps JWT verification enabled.
Authentication at this boundary is provided by Supabase; Koyane does not create a database client
or forward credentials to one.

With the Supabase CLI installed and authenticated, deploy from one generated project directory:

```sh
cd examples/hello-fetch-functions/build/supabase-js
supabase functions deploy hello --project-ref YOUR_PROJECT_REF
```

Use `supabase-wasm` for the Wasm variant.
Invoke with your project's supported authorization headers and a valid JWT:

```sh
curl "https://YOUR_PROJECT_REF.supabase.co/functions/v1/hello/hello/World" \
  -H "Authorization: Bearer $SUPABASE_ACCESS_JWT" \
  -H "apikey: $SUPABASE_PUBLISHABLE_KEY"
```

See [Edge Functions](https://supabase.com/docs/guides/functions) and
[Wasm static files](https://supabase.com/docs/guides/functions/wasm).
The local tests exercise the runtime directly without a Supabase gateway or authentication service.

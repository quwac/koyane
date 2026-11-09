# Cloud Run functions

A common Kotlin HTTP application served through `NodeAdapter` and Google's Node.js Functions Framework.
The framework owns the server and passes native request/response objects to the registered `hello` function.
The JavaScript boundary forwards `rawBody` as UTF-8 text, including JSON and form bodies already parsed by the framework.
This example implements HTTP functions; CloudEvents triggers are outside its scope.

Run from the repository root with the toolchain in [CONTRIBUTING.md](../../CONTRIBUTING.md):

```sh
mise run build:functions:js
mise run build:functions:wasm
mise run test:functions:node
```

Both targets are tested against the official Functions Framework locally.
Cloud deployment is not verified.

## Run locally

The generated `build/cloud-run-js` and `build/cloud-run-wasm` directories contain compiled Kotlin,
an ESM `index.js`, and a `package.json` with the Functions Framework dependency.
Install production dependencies in the generated directory and start the framework:

```sh
cd examples/hello-cloud-run-functions/build/cloud-run-js
npm install --omit=dev
npm start
```

Then call `http://localhost:8080/hello/World`.
Use `cloud-run-wasm` for the Wasm variant.

## Deploy

With Google Cloud CLI configured for your project:

```sh
gcloud run deploy koyane-hello \
  --source examples/hello-cloud-run-functions/build/cloud-run-js \
  --function hello \
  --base-image nodejs22 \
  --region YOUR_REGION
```

Use `cloud-run-wasm` for the Wasm variant.
The service uses your project's IAM policy; invoke an authenticated service with an identity token.
See [writing Cloud Run functions](https://docs.cloud.google.com/run/docs/write-functions) and
[deploying Node.js functions](https://docs.cloud.google.com/run/docs/quickstarts/functions/deploy-functions-gcloud).

# AWS Lambda Web Functions example

This example serves a common Koyane application through `koyane-node`, with Kotlin/JS and Kotlin/Wasm packages.
It uses no database or AWS SDK.
Application routes are in [Application.kt](src/commonMain/kotlin/dev/quwac/koyane/example/lambdaweb/Application.kt).

[Lambda Web Functions](https://docs.aws.amazon.com/lambda/latest/dg/web-functions.html)
run a standard HTTP server and currently support the Node.js 24 runtime.
The example binds to the exact host and port supplied in `AWS_LAMBDA_HTTP_ENDPOINT`.
For local runs with that variable absent, it uses `127.0.0.1:3000`.
The runtime loads `index.js` from the deployment package root.
See the [AWS environment variable reference](https://docs.aws.amazon.com/lambda/latest/dg/web-functions-revisions-env-vars.html).

## Build and run locally

Run from the repository root:

```sh
mise run build:lambda-web:js
node examples/hello-lambda-web/build/lambda-web-js/index.js
```

For Kotlin/Wasm:

```sh
mise run build:lambda-web:wasm
node examples/hello-lambda-web/build/lambda-web-wasm/index.js
```

Use Node.js 24 to match the AWS runtime.
The repository's Node.js 22 toolchain can also run the local HTTP integration tests.
Each build prepares a self-contained directory with `index.js`, `package.json`, and compiled Kotlin modules.
No npm dependencies are required at runtime.

```sh
curl http://127.0.0.1:3000/hello/Kotlin
# Hello, Kotlin!

curl 'http://127.0.0.1:3000/echo?prefix=hello' \
  -H 'X-Message: Kotlin' -d 'world'
# hello:Kotlin:world
```

To change the local listen address:

```sh
AWS_LAMBDA_HTTP_ENDPOINT=127.0.0.1:4000 node examples/hello-lambda-web/build/lambda-web-js/index.js
```

`mise run test:node` builds both targets and tests the packages over real local HTTP connections.
It checks text and UTF-8 bodies, named paths, queries, repeated headers, HTTP failures,
HEAD and 204 responses, concurrent requests, and disconnected clients.
These checks do not establish a successful deployment to AWS.

## Deploy

Install an AWS CLI with the `lambda-web` commands and configure your AWS credentials and Region.
For example, deploy the prepared JavaScript directory:

```sh
aws lambda-web deploy \
  --name koyane-hello \
  --code examples/hello-lambda-web/build/lambda-web-js \
  --runtime nodejs24.x \
  --endpoint-name dev \
  --region ap-northeast-1
```

Use `build/lambda-web-wasm` in `--code` to deploy Kotlin/Wasm.
The command creates or updates AWS resources and may prompt before creating them.
See the [AWS deploy command reference](https://docs.aws.amazon.com/cli/latest/reference/lambda-web/deploy.html)
for execution roles, endpoint authentication, and other deployment settings.

The HTTP adapter uses buffered text bodies.
Streaming and binary responses are outside its current scope.

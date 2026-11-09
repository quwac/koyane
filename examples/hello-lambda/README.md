# Lambda Event Functions with HTTP payload 2.0

A common Kotlin application invoked through `koyane-lambda`.
The ESM entry exports an asynchronous `handler(event)` and opens no listening socket.
It accepts API Gateway HTTP API and Lambda Function URL payload 2.0.
REST API payload 1.0, ALB, SQS, and other non-HTTP events are unsupported.
For a port-listening Lambda application, use the separate [Lambda Web Functions example](../hello-lambda-web/README.md).

Run from the repository root with the toolchain in [CONTRIBUTING.md](../../CONTRIBUTING.md):

```sh
mise run build:functions:js
mise run build:functions:wasm
mise run test:functions:node
```

The tests invoke both native entry points on Node.js with HTTP payload 2.0 events.
They verify Base64 and plain UTF-8 text bodies, query strings, cookies, HEAD, errors,
unsupported events, and concurrent suspend handlers.
AWS deployment is not verified.

## Package and deploy

`build/lambda-js` and `build/lambda-wasm` contain `index.mjs`, `package.json`, and the compiled Kotlin modules.
Zip the directory contents so `index.mjs` is at the archive root:

```sh
cd examples/hello-lambda/build/lambda-js
zip -r ../lambda-js.zip index.mjs package.json kotlin
```

Create a Lambda function with the Node.js 22 or 24 managed runtime and handler `index.handler`.
Upload that zip through the AWS console, CLI, or your infrastructure configuration.
Choose an execution role and memory/timeout limits for your application.
Use `lambda-wasm` for the Wasm variant and include every generated `.mjs` and `.wasm` file.

Connect an API Gateway HTTP API integration with `payloadFormatVersion: "2.0"`, or configure a Lambda Function URL.
For a routed HTTP API, configure the forwarded path/stage to match the application's routes.
Both integrations can invoke `/hello/World`, `POST /echo?prefix=value`, and the other routes declared in `Application.kt`.
Configure authorization in the AWS integration or install the application's authentication plugin.

See [HTTP API payload formats](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-lambda.html)
and [Function URL invocations](https://docs.aws.amazon.com/lambda/latest/dg/urls-invocation.html).

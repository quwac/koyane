# Koyane design

## Targets and module boundaries

Koyane targets Kotlin/JS and Kotlin/Wasm on Cloudflare Workers.
Core owns plugin installation, application attributes, and typed extension lookup.
It has no HTTP, Fetch, or runtime-adapter dependency.

`koyane-http` adds portable HTTP messages, routing, typed input extraction, response builders, and hooks.
Optional behavior lives in separate modules:

| Module | Boundary |
| --- | --- |
| `koyane-content-negotiation` | Explicit media-type codecs |
| `koyane-kotlinx-serialization` | `kotlinx.serialization` JSON codec |
| `koyane-cors` | CORS policy and response headers |
| `koyane-bearer-authentication` | Bearer credential guards |
| `koyane-error-handling` | Exception and HTTP-failure mapping |
| `koyane-logging` | HTTP lifecycle logging |
| `koyane-kotlinx-datetime` | `kotlinx-datetime` input extraction |
| `koyane-testing` | In-memory application testing |
| `koyane-workers` | Cloudflare Workers Fetch adapter |
| `koyane-workers-d1` | D1 binding backed by Kagiroi |

## Applications and plugins

`koyane { ... }` creates an application.
`install(plugin) { ... }` installs a plugin and stores its typed extension in that application.

Plugin identity is the plugin instance, not `equals`, `hashCode`, or a string name.
Installing the same instance twice fails.
Distinct instances have separate configuration and extension state.
Applications and calls each own their attributes.

Plugins register HTTP behavior during `onInstall`.
They must be installed before dispatch.
Missing plugins and invalid external input are runtime errors; the Kotlin compiler cannot establish either condition.

## HTTP lifecycle

`HttpRequest` contains a method, URL, headers, and buffered text body.
Handlers return `HttpResponse`.
`Application.fetch` dispatches the portable request.

1. `onRequest` hooks run before URL parsing.
2. Routing selects a handler and creates a `HandlerScope`.
3. `beforeHandler` hooks and then the handler may return a response.
4. `onResponse` hooks transform that response.
5. `onResponseReady` hooks observe the final response.

An `onRequest` or `beforeHandler` hook may return a response to stop later pre-handler work.
Response hooks apply to handler results, early results, framework errors, and errors recovered by an error hook.

`onError` hooks receive failures in registration order.
The first hook returning a response recovers the request and sends that response through response hooks.
Cancellation always propagates.
Failures from an error hook propagate without another error pass.

Response transformation failures may be recovered.
The failed transformation is skipped for the replacement response during that call, so retries are bounded.
`onResponseReady` is for observations that must run once after transformations.

## Routing and inputs

Routes are strings with literal segments and named `:id` or `{id}` segments.
Both parameter syntaxes can be used in one route.
Literal segments take precedence over parameters.
Duplicate method/path shapes are rejected during registration.

| Condition | Result |
| --- | --- |
| Malformed URL encoding or failed required input conversion | 400 |
| No matching path | 404 |
| Matching path with no handler for the method | 405 with `Allow` |

`param`, `query`, and `header` expose typed extraction functions.
Required values fail with 400 when absent or invalid.
`OrNull` returns null only when the value is absent; an invalid present value still fails with 400.
Defaults apply only when the value is absent.

Query values decode `+` as a space.
Path segments keep `+` literal and keep an encoded slash inside its segment.
Headers match case-insensitively.
Routes and handlers are registered explicitly for GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS, and QUERY.

`body.text()` returns the buffered request body.
The HTTP model does not provide binary or streaming request bodies.

## Responses

`text`, `empty`, and `redirect` create common responses.
The `HttpResponse` constructor accepts other status, body, and header combinations.
Valid statuses are 200 through 599.
204, 205, and 304 reject a nonempty body.

Handlers use ordinary Kotlin values and explicitly convert them to responses.
Koyane does not bind request DTOs, convert arbitrary return values, or infer endpoint response schemas.

## Optional HTTP plugins

`Cors` checks CORS preflight requests before routing and adds CORS headers to allowed responses.
Actual requests still reach routing when their origin is not allowed; CORS controls browser response sharing, not authorization.

`BearerAuthentication<P>` wraps selected handlers with a suspending verifier returning `P?`.
A successful result is available to the guarded handler and later hooks through its plugin instance.
Missing or rejected Bearer credentials produce the appropriate 401 challenge; malformed credentials produce 400.

`ErrorHandling` maps registered exception types and HTTP statuses to responses.
HTTP-classified failures use status mappings before exception mappings.
With `ErrorHandling` installed, unmapped application failures become an empty 500 response.

`Logging` records request start, final response status, and unhandled failures through an application-configured sink.

## Content negotiation

`ContentNegotiation` registers codecs explicitly.
Request decoding uses `Content-Type`; response encoding uses `Accept`.
Missing, malformed, or unsupported request content types return 415.
An unacceptable response representation returns 406.
Codec decoding failures return 400.

`koyane-kotlinx-serialization` adds `json()`, `receive`, and `respond` extensions.
Callers pass a `KSerializer<T>` for both decoding and encoding.
Framework modules do not apply a serialization compiler plugin or discover serializers.

## Testing and Workers

`testApplication` creates an isolated application and sends portable requests through the production HTTP pipeline.
It does not open a port or start a Workers runtime.

`defineWorker` installs a target-specific Fetch entry point.
The adapter converts native Fetch requests and responses at the target boundary.
It creates the application from the first request environment and reuses it for the Worker isolate.

`defineD1Worker` obtains the Cloudflare D1 binding and exposes a Kagiroi `D1Database` to application setup.
The example keeps its `main` function in `commonMain` through this adapter.

## Deliberate limits

Koyane has no JVM server runtime, reflection-based dependency injection, route annotations,
KSP integration, compiler plugins, automatic request binding, OpenAPI generation, or generated clients.
Applications supply host configuration, serializers, authentication policy, and persistence code explicitly.

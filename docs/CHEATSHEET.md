# Koyane cheat sheet

Examples target Kotlin/JS and Kotlin/Wasm. Core and HTTP are runtime-independent.
Each Kotlin block is a separate example with its own imports.
See [Dependencies](../README.md#dependencies) for BOM-based dependencies and source-module usage.
Add the optional modules used by your application explicitly.
The `dev.quwac.koyane:koyane-bom` platform aligns versions; it does not add libraries or install plugins.

| Module | Import package |
| --- | --- |
| `koyane-core` | `dev.quwac.koyane.core` |
| `koyane-runtime` | `dev.quwac.koyane.runtime` (common and target source sets) |
| `koyane-http` | `dev.quwac.koyane.http` |
| `koyane-content-negotiation` | `dev.quwac.koyane.contentnegotiation` |
| `koyane-kotlinx-serialization` | `dev.quwac.koyane.serialization.kotlinx` |
| `koyane-bearer-authentication` | `dev.quwac.koyane.authentication.bearer` |
| `koyane-cors` | `dev.quwac.koyane.cors` |
| `koyane-error-handling` | `dev.quwac.koyane.errorhandling` |
| `koyane-logging` | `dev.quwac.koyane.logging` |
| `koyane-kotlinx-datetime` | `dev.quwac.koyane.http` |
| `koyane-testing` | `dev.quwac.koyane.testing` |
| `koyane-cloudflare-workers` | `dev.quwac.koyane.cloudflare.workers` (common and target source sets) |
| `koyane-node` | `dev.quwac.koyane.node` |
| `koyane-fetch` | `dev.quwac.koyane.fetch` (common and target source sets) |
| `koyane-lambda` | `dev.quwac.koyane.lambda` (common and target source sets) |

## Application and routing

Install `Http` explicitly. Every handler returns an `HttpResponse`.

```kotlin
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpStatus

val app = koyane {
    install(Http) {
        get("/hello/:name") { text("Hello, ${param.string("name")}!") }
        post("/echo") { text(body.text(), HttpStatus.CREATED) }
        put("/users/{id}") { text("Updated ${param.long("id")}") }
        patch("/users/{id}") { text("Patched ${param.long("id")}") }
        delete("/users/{id}") { empty(HttpStatus.NO_CONTENT) }
        head("/health") { empty(HttpStatus.OK) }
        options("/health") { empty(HttpStatus.NO_CONTENT) }
        query("/search") { text(body.text()) }
    }
}
```

| Form | Meaning |
| --- | --- |
| `get("/users/me") { ... }` | Literal path |
| `get("/users/:id") { ... }` | Named path parameter |
| `get("/users/{id}") { ... }` | Equivalent named parameter syntax |
| `route(HttpMethod.GET, "/users") { ... }` | Explicit method registration |
| `query("/search") { ... }` | HTTP QUERY method |
| `query.string("q")` | URL query value inside a handler |

Both parameter syntaxes can appear in one route. Literal segments take precedence over parameters.
Duplicate method/path shapes fail during registration.
Unknown paths return 404; a matching path without a handler for its method returns 405 with `Allow`.
Register HEAD and OPTIONS handlers explicitly; GET does not register them automatically.

## Typed request inputs

`param`, `query`, and `header` expose the same extraction functions.
Header names are case-insensitive.

```kotlin
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http

data class PageSize(val value: Int)

val app = koyane {
    install(Http) {
        get("/organizations/:organizationId/users/{userId}") {
            val organizationId = param.uuid("organizationId")
            val userId = param.long("userId")
            val page = query.int("page", default = 1)
            val enabled = query.boolean("enabled", default = true)
            val search = query.stringOrNull("q")
            val requestId = header.stringOrNull("X-Request-Id")
            val limit = query.parse("limit", default = PageSize(20)) { value ->
                value.toIntOrNull()?.takeIf { it in 1..100 }?.let(::PageSize)
            }
            text("$organizationId/$userId/$page/$enabled/$search/$requestId/${limit.value}")
        }
    }
}
```

| Function | Kotlin type | Default argument |
| --- | --- | --- |
| `string(name)` | `String` | No |
| `boolean(name)` | `Boolean` | Yes |
| `byte`, `short`, `int`, `long` | `Byte`, `Short`, `Int`, `Long` | Yes |
| `ubyte`, `ushort`, `uint`, `ulong` | `UByte`, `UShort`, `UInt`, `ULong` | Yes |
| `float`, `double` | `Float`, `Double` | Yes |
| `uuid(name)` | `kotlin.uuid.Uuid` | No |
| `instant(name)` | `kotlin.time.Instant` | Yes |
| `duration(name)` | `kotlin.time.Duration` | Yes |
| `parse(name) { ... }` | Parser result `T : Any` | Yes |

Each function has an `OrNull` form, such as `longOrNull(name)` or `parseOrNull(name) { ... }`.
Required missing values and invalid present values produce 400.
`OrNull` returns null only for missing values; defaults also apply only to missing values.
Boolean input accepts lowercase `true` or `false`.
UUID input uses the hyphenated format. A custom parser returns null to reject input;
unexpected parser exceptions propagate as application failures.

URL query values decode `+` as a space. Path segments preserve literal `+` and encoded slashes within a segment.
`body.text()` returns the buffered request body. Binary and streaming bodies are not supported.

### kotlinx-datetime inputs

Add `koyane-kotlinx-datetime` for these extensions, all with required, default, and `OrNull` forms:

| Function | Kotlin type |
| --- | --- |
| `localDate` | `kotlinx.datetime.LocalDate` |
| `localTime` | `kotlinx.datetime.LocalTime` |
| `localDateTime` | `kotlinx.datetime.LocalDateTime` |
| `utcOffset` | `kotlinx.datetime.UtcOffset` |
| `timeZone` | `kotlinx.datetime.TimeZone` |

```kotlin
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.localDate
import dev.quwac.koyane.http.timeZone
import kotlinx.datetime.TimeZone

val app = koyane {
    install(Http) {
        get("/events") {
            val date = query.localDate("date")
            val zone = query.timeZone("zone", default = TimeZone.UTC)
            text("$date/$zone")
        }
    }
}
```

## Responses

| Expression | Result |
| --- | --- |
| `text("hello")` | 200, `text/plain; charset=utf-8` |
| `text("created", HttpStatus.CREATED)` | 201 with a text body |
| `empty(HttpStatus.NO_CONTENT)` | 204 with no body |
| `redirect("/login")` | 302 with `Location` |
| `redirect("/users/1", HttpStatus.SEE_OTHER)` | 303 with `Location` |
| `HttpResponse(202, "queued", mapOf("X-Job-Id" to "123"))` | Explicit status, body, and headers |

Response constructors validate statuses at runtime: 200 through 599, with an empty body for 204, 205, and 304.
Redirects accept 301, 302, 303, 307, and 308; locations must be nonempty and contain no CR or LF.
Handlers can map sealed application results with an exhaustive Kotlin `when`:

```kotlin
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpStatus

sealed interface UserResult {
    data class Found(val name: String) : UserResult
    data object NotFound : UserResult
}

fun findUser(id: Long): UserResult =
    if (id == 1L) UserResult.Found("Ada") else UserResult.NotFound

val app = koyane {
    install(Http) {
        get("/users/:id") {
            when (val result = findUser(param.long("id"))) {
                is UserResult.Found -> text(result.name)
                UserResult.NotFound -> empty(HttpStatus.NOT_FOUND)
            }
        }
    }
}
```

## JSON and content negotiation

Add `koyane-content-negotiation` and `koyane-kotlinx-serialization`.
Install `ContentNegotiation`, register `json()`, and pass a serializer for each value.
This example uses built-in serializers and needs no application serialization compiler plugin.

```kotlin
import dev.quwac.koyane.contentnegotiation.ContentNegotiation
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpStatus
import dev.quwac.koyane.serialization.kotlinx.json
import dev.quwac.koyane.serialization.kotlinx.receive
import dev.quwac.koyane.serialization.kotlinx.respond
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

val messagesSerializer = ListSerializer(String.serializer())

val app = koyane {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(Http) {
        post("/messages") {
            val messages = receive(messagesSerializer)
            respond(messages, messagesSerializer, status = HttpStatus.CREATED)
        }
    }
}
```

| Condition | Response |
| --- | --- |
| Missing, malformed, or unsupported request `Content-Type` | 415 |
| Body rejected by the codec | 400 |
| No acceptable representation for `Accept` | 406 |
| Missing `Accept` | First supported registered representation |

For application classes, pass a `KSerializer<T>` explicitly.
Applications using `@Serializable` and generated `.serializer()` functions apply Kotlin's serialization plugin themselves.
`HttpCall.respond(value, serializer)` also encodes responses from HTTP hooks.

For another format, implement `ContentConverter` and `ContentCodec<T>`, register the converter with
`register(ContentType(...), converter)`, and supply a converter-to-codec function to the
`receive` and `respond` extensions in `dev.quwac.koyane.contentnegotiation`.
HTTP and Core do not depend on a JSON library.

```kotlin
import dev.quwac.koyane.contentnegotiation.ContentCodec
import dev.quwac.koyane.contentnegotiation.ContentConverter
import dev.quwac.koyane.contentnegotiation.ContentNegotiation
import dev.quwac.koyane.contentnegotiation.receive
import dev.quwac.koyane.contentnegotiation.respond
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.ContentType
import dev.quwac.koyane.http.Http

object PlainNumber : ContentConverter

object LongTextCodec : ContentCodec<Long> {
    override fun decode(body: String): Long? = body.toLongOrNull()
    override fun encode(value: Long): String = value.toString()
}

val app = koyane {
    install(ContentNegotiation) {
        register(ContentType("text", "plain"), PlainNumber)
    }
    install(Http) {
        post("/increment") {
            val value = receive { if (it === PlainNumber) LongTextCodec else null }
            respond(value + 1L) { if (it === PlainNumber) LongTextCodec else null }
        }
    }
}
```

## Bearer authentication

Keep one `BearerAuthentication<P>` instance for installation, handler guards, and principal lookup.
Supply a suspending verifier that returns `P` for an accepted token or null for rejection.

```kotlin
import dev.quwac.koyane.authentication.bearer.BearerAuthentication
import dev.quwac.koyane.authentication.bearer.authenticated
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http

data class UserPrincipal(val name: String)
val users = BearerAuthentication<UserPrincipal>()

val app = koyane {
    install(users) {
        realm = "example"
        verify { token ->
            // Replace this demonstration check with your token verification.
            if (token == "sample-token") UserPrincipal("Ada") else null
        }
    }
    install(Http) {
        get("/public") { text("hello") }
        get("/me", authenticated(users) { user -> text(user.name) })
    }
}
```

Only wrapped handlers require authentication; verification runs after routing and before-handler hooks.
Missing or rejected credentials return 401 with `WWW-Authenticate`; malformed Bearer credentials return 400.
Verifier exceptions are application failures.
Import `principal` or `principalOrNull` from the Bearer package to read the principal in a handler;
response and error hooks can use `principalOrNull(users)` after successful verification.

## CORS

```kotlin
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.cors.Cors
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpMethod

val app = koyane {
    install(Cors) {
        allowOrigin("https://client.example")
        allowMethod(HttpMethod.PUT)
        allowMethod(HttpMethod.DELETE)
        allowHeader("Content-Type")
        allowHeader("Authorization")
        exposeHeader("X-Request-Id")
        allowCredentials = true
        maxAgeSeconds = 600
    }
    install(Http) {
        get("/hello") { text("hello") }
    }
}
```

GET, HEAD, and POST are allowed by default. Origins must be serialized HTTP(S) origins without a trailing slash.
Use `allowAnyOrigin()` for `*`; it cannot be combined with `allowCredentials = true`.
Allowed preflights return 204, disallowed preflights return 403, and malformed preflights return 400.
Actual requests still reach routing when their origin is disallowed; CORS controls browser response sharing.

## Error handling and logging

```kotlin
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.errorhandling.ErrorHandling
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpStatus
import dev.quwac.koyane.http.text
import dev.quwac.koyane.logging.Logging

class InvalidName : RuntimeException()

val app = koyane {
    install(Logging) {
        sink = { message -> println(message) }
    }
    install(ErrorHandling) {
        exception<InvalidName> { text("Invalid name", HttpStatus.BAD_REQUEST) }
        status(HttpStatus.BAD_REQUEST) { text("Invalid request", HttpStatus.BAD_REQUEST) }
        status(HttpStatus.NOT_FOUND) { text("Not found", HttpStatus.NOT_FOUND) }
    }
    install(Http) {
        get("/users/:id") { text(param.long("id").toString()) }
        post("/names") {
            if (body.text().isBlank()) throw InvalidName()
            text(body.text(), HttpStatus.CREATED)
        }
    }
}
```

Exception mappings use first-match order; register specific types before `Throwable`.
Framework `HttpFailure` errors use status mappings, and each mapping must return its registered status.
Status mappings do not rewrite responses returned directly by handlers.
Unmapped application failures become an empty 500 with `ErrorHandling` installed.
Without it or another recovering hook, unclassified exceptions propagate.
Cancellation propagates in either case.

`Logging` records request start, final response status, and errors reaching its error hook.
Install it before `ErrorHandling` to observe failures that `ErrorHandling` recovers.
`application[Logging].log("message")` uses the configured sink; the default sink is `println`.

## Custom plugins and attributes

Implement `Plugin<Configuration, Extension>` and use the same instance for installation and lookup.
Install dependencies before the plugin that uses them. Installing the same plugin instance twice fails at runtime.
Attributes use typed key instances; two keys with the same name remain distinct.

```kotlin
import dev.quwac.koyane.core.AttributeKey
import dev.quwac.koyane.core.Plugin
import dev.quwac.koyane.core.PluginScope
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.beforeHandler
import dev.quwac.koyane.http.onResponse

class RequestIdConfiguration {
    var prefix: String = "request"
}

val requestIdKey = AttributeKey<String>("Request ID")

object RequestIds : Plugin<RequestIdConfiguration, String> {
    override fun createConfiguration() = RequestIdConfiguration()
    override fun install(configuration: RequestIdConfiguration) = configuration.prefix

    override fun onInstall(scope: PluginScope, extension: String) {
        scope.beforeHandler {
            attributes[requestIdKey] = "$extension-${kotlin.uuid.Uuid.random()}"
            null
        }
        scope.onResponse { response ->
            val id = attributes.getOrNull(requestIdKey)
            if (id == null) response else response.copy(headers = response.headers + ("X-Request-Id" to id))
        }
    }
}

val app = koyane {
    install(RequestIds) { prefix = "api" }
    install(Http) {
        get("/request-id") { text(attributes[requestIdKey]) }
    }
}

val prefix: String = app[RequestIds]
```

`application.attributes` lives for the application; `HttpCall.attributes` and the handler's `attributes`
belong to one request. Read with `[key]` or `getOrNull(key)`, check `key in attributes`, and remove with `remove(key)`.
Plugin presence and missing attributes are runtime checks.

| Hook on `PluginScope` | Receiver | Return value |
| --- | --- | --- |
| `onRequest` | `HttpCall` | `HttpResponse?`; non-null skips routing and the handler |
| `beforeHandler` | `HandlerScope` | `HttpResponse?`; non-null skips the matched handler |
| `onResponse` | `HttpCall` | Transformed `HttpResponse` |
| `onResponseReady` | `HttpCall` | `Unit`; observes the final response once |
| `onError` | `HttpCall` | `HttpResponse?`; first non-null response recovers the failure |

Hooks run in registration order. Response transformations also apply to early and framework responses.
After a response transformation fails and is recovered, transformations restart with failed hooks skipped.
Use `onResponseReady` for observations that must not repeat; its failures propagate without recovery.
Cancellation and errors thrown by error hooks propagate.

## In-memory application tests

Add `koyane-testing` to your test source set. `testApplication` runs the production HTTP pipeline without opening a port.
Configure the application once, before sending requests.

```kotlin
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpStatus
import dev.quwac.koyane.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class EchoTest {
    @Test
    fun echo() = testApplication {
        application {
            install(Http) {
                post("/echo") { text("${query.string("q")}:${body.text()}", HttpStatus.CREATED) }
            }
        }

        val response = client.post("/echo") {
            query("q", "hello world")
            header("Content-Type", "text/plain")
            body = "payload"
        }

        assertEquals(201, response.status)
        assertEquals("hello world:payload", response.bodyAsText())
        assertEquals("text/plain; charset=utf-8", response.header("content-type"))
        assertEquals(404, client.get("/missing").status)
    }
}
```

The client supports `get`, `post`, `put`, `patch`, `delete`, `head`, `options`, `query`,
and `request(HttpMethod, url)`. Each accepts headers, a text body, and a request builder.
`response.headers` exposes all response headers; `header(name)` matches case-insensitively.

## Host entry points from common code

All host factories accept a shared `Environment` and a resource-owning receiver on JS and Wasm.
Build the application, read configuration, and acquire dependencies in `commonMain`.
Native request, response, event, and Promise handling stays in the adapters.

### Service support

| Entry point | Module | Services |
| --- | --- | --- |
| `cloudflareWorker { env -> koyane { ... } }` | `koyane-cloudflare-workers` | Cloudflare Workers |
| `fetchFunction { env -> koyane { ... } }` | `koyane-fetch` | Vercel Functions, Supabase Edge Functions |
| `nodeFunction { env -> koyane { ... } }` | `koyane-node` | Google Cloud Run functions |
| `awsLambdaFunction { env -> koyane { ... } }` | `koyane-lambda` | AWS Lambda Event Functions (HTTP payload 2.0) |
| `nodeServer { env -> koyane { ... } }` | `koyane-node` | AWS Lambda Web Functions, Node.js servers on VMs and containers |

See [Supported services](../README.md#supported-services) for runtime and deployment boundaries.

### Application and resource lifetimes

| Entry point | Application and resource lifetime |
| --- | --- |
| `cloudflareWorker { env -> koyane { ... } }` | One request |
| `fetchFunction { env -> koyane { ... } }` | One Fetch invocation |
| `nodeFunction { env -> koyane { ... } }` | One native Node invocation |
| `awsLambdaFunction { env -> koyane { ... } }` | One Lambda HTTP invocation |
| `nodeServer { env -> koyane { ... } }` | One server, until `close()` finishes |

`Environment.getString(name)` reads a required string.
`getStringOrNull(name)` returns null for missing or null properties and rejects present values of another type.
`environment.get(name).getString(property)` reads a required nested object property.
Invalid values are rejected without including their contents in error messages.
Native environment objects and handles are viewed without serialization.
`environment.get(name, convert)` passes a required native object into a library's common typed factory.
For example, `environment.get("DB", ::D1Binding)` creates a Kagiroi D1 handle without either library depending on the other.
The conversion boundary uses `Any`; application code receives the factory's typed result.

Factories use `ResourceScope.resource(create, release)` to acquire dependencies.
Function entry points release resources in reverse acquisition order after buffered response processing,
including factory, handler, conversion, cancellation, and release failures.
`nodeServer` retains resources until server shutdown has finished waiting for active requests.
An operation failure stays primary; release failures are suppressed.
A failed acquisition releases earlier resources.
`withResourceScope { ... }` applies the same lifetime to common tests and non-HTTP operations.
A standalone `ResourceScope` can also be closed explicitly; its resources must not escape their owner's lifetime.
`close()` waits for pending acquisitions and releases each resource once, even when close calls overlap.
An acquisition that completes after closing starts registers its resource for release and fails.
These primitives are in `koyane-runtime`, which depends on Core and coroutines and has no HTTP or database dependency.

Each function entry point accepts one registration per module. Its native export receives
host values and an environment object; the host's JavaScript wrapper only forwards those values:

| Registration | Native export | Native arguments |
| --- | --- | --- |
| `cloudflareWorker` | `fetch` | request, Cloudflare environment |
| `fetchFunction` | `handleFetchRequest` | request, environment object |
| `nodeFunction` | `handleNodeRequest` | request, response, original UTF-8 body, environment object |
| `awsLambdaFunction` | `handleLambdaEvent` | HTTP payload 2.0 event, environment object |

For Node-based functions pass `process.env`; for Supabase Edge Functions pass `Deno.env.toObject()`.
The standalone Node server obtains `process.env` inside the adapter.
On JS, import the application's executable module before invoking the native export from its adapter module.
On Wasm, the linked executable exports the same native function names.
The [Fetch](../examples/hello-fetch-functions/README.md), [Cloud Run](../examples/hello-cloud-run-functions/README.md),
and [Lambda](../examples/hello-lambda/README.md) examples include ready-to-run host packages.

## Cloudflare Workers adapter

`koyane-cloudflare-workers` provides `cloudflareWorker` from `commonMain` on JS and Wasm.
It creates one application per request and passes a validated `Environment` view.
Read external strings with `Environment.getString`. Use `Environment.get` to view nested bindings
and read their string properties with `Environment.getString`.
Invalid shapes are rejected without including their values in errors.
The adapter keeps native binding objects in the host; it does not serialize them.

```kotlin
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.cloudflare.workers.cloudflareWorker

fun main() {
    cloudflareWorker { environment ->
        val greeting = environment.getString("GREETING")
        koyane {
            install(Http) { get("/hello") { text(greeting) } }
        }
    }
}
```

The factory has a `ResourceScope` receiver. Acquire request-owned values inside the factory:

```kotlin
val client = resource(create = { openClient() }, release = { it.close() })
```

The adapter releases acquired resources in reverse order after buffered response processing,
including factory, handler, conversion, cancellation, and release failures.
A failed acquisition releases earlier acquisitions. An operation failure remains primary;
release failures are suppressed. `withResourceScope` provides the same lifetime for
common tests and non-HTTP operations. Do not retain request resources beyond the scope.
The adapter is independent of databases, HTTP clients, and DI libraries.
The module accepts one application factory.
The JavaScript entry point forwards the request and environment to the adapter's native `fetch` export.
For Wrangler packaging, local execution, and D1 migrations, see the
[Cloudflare Workers example](../examples/hello-workers/README.md).

## Node.js HTTP adapter

`koyane-node` supports Kotlin/JS and Kotlin/Wasm with the same common API.

`nodeServer` configures and creates a server from common code:

```kotlin
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.node.nodeServer

suspend fun main() {
    val server = nodeServer { environment ->
        port = environment.getStringOrNull("PORT")?.toInt() ?: 3000
        koyane { install(Http) { get("/hello") { text("Hello") } } }
    }
    println("Listening on ${server.host}:${server.port}")
}
```

The factory runs once with `NodeServerScope`, which combines `ResourceScope` with mutable `host` and `port`.
Factory or bind failures release acquired resources. `NodeServer.close` waits for active requests
and then releases factory resources, including when shutdown is cancelled or fails.
`Application.listen` remains available when the caller already owns the application and its resources.

```kotlin
import dev.quwac.koyane.core.Application
import dev.quwac.koyane.node.NodeServer
import dev.quwac.koyane.node.listen

suspend fun startServer(application: Application): NodeServer =
    application.listen(host = "127.0.0.1", port = 3000)
```

`listen` requires an installed `Http` plugin and returns after the socket is listening.
`NodeServer.port` contains the bound port; setting the port to zero chooses an available port.
`close` stops accepting connections and waits for active HTTP requests to finish.
The application instance handles concurrent requests, so application-owned mutable state must be safe for concurrent use.
The adapter buffers incoming UTF-8 text, joins repeated request headers, and omits HEAD response bodies.
Unhandled failures produce a generic 500 response; install `ErrorHandling` for application-defined error responses.
The adapter does not implement streaming, binary bodies, WebSockets, or AWS event payloads.

AWS Lambda Web Functions use the host and port from `AWS_LAMBDA_HTTP_ENDPOINT`.
Read it at the application boundary as shown in the
[Lambda Web Functions example](../examples/hello-lambda-web/README.md).

## Fetch function adapter

`fetchFunction` registers a common application factory for Vercel, Supabase, and other Fetch hosts:

```kotlin
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.fetch.fetchFunction
import dev.quwac.koyane.http.Http

fun main() {
    fetchFunction { environment ->
        val greeting = environment.getStringOrNull("GREETING") ?: "Hello"
        koyane { install(Http) { get("/hello") { text(greeting) } } }
    }
}
```

The native `handleFetchRequest` export owns each request's resources.
For an already-owned application, `FetchAdapter.handle` remains available
with native `FetchRequest`, `FetchResponse`, `FetchHeaders`, and Promise types in the target source sets.
Its suspending `FetchAdapter.handleRequest` variant runs in the caller's coroutine context.

The adapter buffers the request's text body, preserves its URL and headers,
and omits bodies for HEAD and bodyless HTTP statuses.
Unhandled failures reject the promise; install `ErrorHandling` to return an HTTP error response.
`CloudflareWorkerAdapter` and the `CloudflareWorker*` native types are aliases for this Fetch API.
See the [Vercel and Supabase example](../examples/hello-fetch-functions/README.md) for host entry points and Wasm packaging.

## Node.js function adapter

`nodeFunction` registers the application factory in common code:

```kotlin
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.node.nodeFunction

fun main() {
    nodeFunction { environment ->
        val greeting = environment.getStringOrNull("GREETING") ?: "Hello"
        koyane {
            install(Http) { get("/hello") { text(greeting) } }
        }
    }
}
```

Its receiver is `ResourceScope`; the factory's result is an `Application`.
The native `handleNodeRequest` export owns resources for one invocation.
Factory and release failures reject its promise; handler failures retain the native adapter's 500-response behavior.

For an already-owned application, `NodeAdapter.handle` accepts native `NodeRequest` and `NodeResponse` values
and the original UTF-8 body as a `String`.
These target-specific types are in `dev.quwac.koyane.node` on both JS and Wasm.
The returned native promise resolves after ending the response.
The suspending `NodeAdapter.handleRequest` variant preserves the caller's coroutine context;
cancellation propagates after the native failure response is attempted.
It requires an installed `Http` plugin, shares the server adapter's response/error behavior,
and opens no listening socket.
For Cloud Run functions, the JavaScript boundary passes the original UTF-8 text from the request's raw body.
The parsed body may no longer contain the original text, and its stream may already be consumed.
See the [Cloud Run functions example](../examples/hello-cloud-run-functions/README.md).

## Lambda HTTP event adapter

`awsLambdaFunction` registers the application factory in common code:

```kotlin
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.lambda.awsLambdaFunction

fun main() {
    awsLambdaFunction { environment ->
        val greeting = environment.getStringOrNull("GREETING") ?: "Hello"
        koyane {
            install(Http) { get("/hello") { text(greeting) } }
        }
    }
}
```

Its receiver is `ResourceScope`; the factory's result is an `Application`.
The native `handleLambdaEvent` export owns resources for one HTTP invocation.

For an already-owned application, `LambdaAdapter.handle` accepts a native `LambdaHttpEvent`
and returns `Promise<LambdaHttpResponse>` on JS and Wasm, in `dev.quwac.koyane.lambda`.
The suspending `LambdaAdapter.handleEvent` variant preserves the caller's coroutine context.
An installed `Http` plugin is required.
Only API Gateway HTTP API and Function URL payload 2.0 are supported.
The adapter preserves `rawPath` and `rawQueryString`, reads UTF-8 text or Base64-encoded UTF-8 text,
joins event cookies with a semicolon and space, and returns `statusCode`, `headers`, `cookies`, and `body`.
The response's `isBase64Encoded` flag is false.
Response `Set-Cookie` values go into the separate cookies array without splitting on commas.
HEAD responses have an empty body.
The HTTP header model stores one string per name; use one Set-Cookie value per response header entry.
Unsupported event formats, invalid Base64, and unhandled application failures reject the promise.
Install `ErrorHandling` to produce application-defined HTTP error responses.
See the [Lambda Event Functions example](../examples/hello-lambda/README.md).

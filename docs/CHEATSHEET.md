# Koyane cheat sheet

Examples target Kotlin/JS and Kotlin/Wasm. Core and HTTP are runtime-independent.
Each Kotlin block is a separate example with its own imports.
See [Dependencies](../README.md#dependencies) for BOM-based dependencies and source-module usage.
Add the optional modules used by your application explicitly.
The `dev.koyane:koyane-bom` platform aligns versions; it does not add libraries or install plugins.

| Module | Import package |
| --- | --- |
| `koyane-core` | `dev.quwac.koyane.core` |
| `koyane-http` | `dev.quwac.koyane.http` |
| `koyane-content-negotiation` | `dev.quwac.koyane.contentnegotiation` |
| `koyane-kotlinx-serialization` | `dev.quwac.koyane.serialization.kotlinx` |
| `koyane-bearer-authentication` | `dev.quwac.koyane.authentication.bearer` |
| `koyane-cors` | `dev.quwac.koyane.cors` |
| `koyane-error-handling` | `dev.quwac.koyane.errorhandling` |
| `koyane-logging` | `dev.quwac.koyane.logging` |
| `koyane-kotlinx-datetime` | `dev.quwac.koyane.http` |
| `koyane-testing` | `dev.quwac.koyane.testing` |
| `koyane-workers` | `dev.quwac.koyane.workers` (target source set) |
| `koyane-node` | `dev.quwac.koyane.node` |
| `koyane-fetch` | `dev.quwac.koyane.fetch` (target source set) |
| `koyane-lambda` | `dev.quwac.koyane.lambda` (target source set) |

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

## Cloudflare Workers adapter

`koyane-workers` supplies target-specific `defineWorker` and `WorkersAdapter` APIs for Fetch hosts.
Keep host environment types in the target source set.
Declare a typed environment and pass application dependencies explicitly.
This example works in `jsMain` and `wasmJsMain`:

```kotlin
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.workers.defineWorker
import kotlin.js.JsAny

external interface WorkerEnvironment : JsAny {
    val greeting: String
}

fun main() {
    defineWorker<WorkerEnvironment> { environment ->
        koyane {
            install(Http) {
                get("/hello") { text(environment.greeting) }
            }
        }
    }
}
```

The adapter initializes the application from the first request environment and reuses it within the Worker isolate.
The JavaScript Fetch entry point passes the full environment as `fetchKoyane(request, env)`.
The adapter does not depend on a database or driver.
Construct any database client supported by the target runtime in the application factory
and pass it to common application code.
See the [Workers example](../examples/hello-workers/README.md) for D1 queries, migrations, packaging, and deployment.

## Node.js HTTP adapter

`koyane-node` supports Kotlin/JS and Kotlin/Wasm with the same common API:

```kotlin
import dev.quwac.koyane.core.Application
import dev.quwac.koyane.node.NodeServer
import dev.quwac.koyane.node.listen

suspend fun startServer(application: Application): NodeServer =
    application.listen(host = "127.0.0.1", port = 3000)
```

`listen` requires an installed `Http` plugin and returns after the socket is listening.
`server.port` contains the bound port; `port = 0` chooses an available port.
`close` stops accepting connections and waits for active HTTP requests to finish.
The application instance handles concurrent requests, so application-owned mutable state must be safe for concurrent use.
The adapter buffers incoming UTF-8 text, joins repeated request headers, and omits HEAD response bodies.
Unhandled failures produce a generic 500 response; install `ErrorHandling` for application-defined error responses.
The adapter does not implement streaming, binary bodies, WebSockets, or AWS event payloads.

AWS Lambda Web Functions use the host and port from `AWS_LAMBDA_HTTP_ENDPOINT`.
Read it at the application boundary as shown in the
[Lambda Web Functions example](../examples/hello-lambda-web/README.md).

## Fetch function adapter

`koyane-fetch` exposes `FetchAdapter`, `FetchRequest`, `FetchResponse`, and `FetchHeaders`
in `jsMain` and `wasmJsMain`. An adapter reuses an application and returns a native promise:

```kotlin
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.fetch.FetchAdapter
import dev.quwac.koyane.fetch.FetchRequest
import dev.quwac.koyane.fetch.FetchResponse
import kotlin.js.Promise

class FetchFunction(application: Application) {
    private val adapter = FetchAdapter(application)
    fun handle(request: FetchRequest): Promise<FetchResponse> = adapter.handle(request)
}
```

The adapter buffers the request's text body, preserves its URL and headers,
and omits bodies for HEAD and bodyless HTTP statuses.
Unhandled failures reject the promise; install `ErrorHandling` to return an HTTP error response.
`WorkersAdapter` and the `Worker*` native types are aliases for this Fetch API.
See the [Vercel and Supabase example](../examples/hello-fetch-functions/README.md) for host entry points and Wasm packaging.

## Node.js function adapter

`NodeAdapter(application).handle(request, response, body)` accepts native `NodeRequest` and
`NodeResponse` values and the original UTF-8 body as a `String`.
These target-specific types are in `dev.quwac.koyane.node` on both JS and Wasm.
The returned native promise resolves after ending the response.
It requires an installed `Http` plugin, shares the server adapter's response/error behavior,
and opens no listening socket.
For Cloud Run functions, pass `request.rawBody?.toString("utf8") ?? ""` from the JavaScript boundary;
`request.body` can already be parsed and its stream consumed.
See the [Cloud Run functions example](../examples/hello-cloud-run-functions/README.md).

## Lambda HTTP event adapter

`LambdaAdapter(application).handle(event)` accepts a native `LambdaHttpEvent` and returns a
`Promise<LambdaHttpResponse>` on JS and Wasm, in `dev.quwac.koyane.lambda`.
An installed `Http` plugin is required.
Only API Gateway HTTP API and Function URL payload 2.0 are supported.
The adapter preserves `rawPath` and `rawQueryString`, reads UTF-8 text or Base64-encoded UTF-8 text,
joins event cookies with `; `, and returns `statusCode`, `headers`, `cookies`, `body`, and `isBase64Encoded: false`.
Response `Set-Cookie` values go into the separate cookies array without splitting on commas.
HEAD responses have an empty body.
The HTTP header model stores one string per name; use one Set-Cookie value per response header entry.
Unsupported event formats, invalid Base64, and unhandled application failures reject the promise.
Install `ErrorHandling` to produce application-defined HTTP error responses.
See the [Lambda Event Functions example](../examples/hello-lambda/README.md).

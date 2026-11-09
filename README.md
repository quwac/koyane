<p align="center">
  <img src="docs/assets/koyane-logo.svg" alt="Koyane logo" width="420">
</p>

<h1 align="center">Koyane</h1>

<p align="center">
  <a href="https://github.com/quwac/koyane/actions/workflows/ci.yml">
    <img src="https://img.shields.io/badge/CI-Verify-2088FF?logo=githubactions&amp;logoColor=white" alt="CI: Verify">
  </a>
  <a href="https://kotlinlang.org/">
    <img src="https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&amp;logoColor=white" alt="Kotlin 2.4.21">
  </a>
  <a href="https://kotlinlang.org/docs/js-overview.html">
    <img src="https://img.shields.io/badge/Kotlin-JS-7F52FF?logo=kotlin&amp;logoColor=white" alt="Kotlin/JS">
  </a>
  <a href="https://kotlinlang.org/docs/wasm-overview.html">
    <img src="https://img.shields.io/badge/Kotlin-Wasm-7F52FF?logo=kotlin&amp;logoColor=white" alt="Kotlin/Wasm">
  </a>
  <a href="https://gradle.org/">
    <img src="https://img.shields.io/badge/Gradle-9.8.2-02303A?logo=gradle&amp;logoColor=white" alt="Gradle 9.8.2">
  </a>
  <a href="https://openjdk.org/">
    <img src="https://img.shields.io/badge/JDK-17-007396?logo=openjdk&amp;logoColor=white" alt="JDK 17">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="MIT License">
  </a>
</p>

Koyane (こやね, pronounced "koh-yah-neh") is a Kotlin framework with a typed plugin API.
Applications are written in common Kotlin and built for Kotlin/JS or Kotlin/Wasm.
Core and HTTP are runtime-independent; adapters connect applications to host runtimes.

The API is experimental.

- [Cheat sheet](docs/CHEATSHEET.md)
- [Workers example](examples/hello-workers/README.md)
- [Contributing](CONTRIBUTING.md)

## Application

Use the source modules in this repository from a Gradle multi-project build.

```kotlin
commonMain.dependencies {
    implementation(project(":koyane-http"))
}
```

Install HTTP explicitly and return an `HttpResponse` from every handler.

```kotlin
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.text

val app = koyane {
    install(Http) {
        get("/organizations/:organizationId/users/{userId}") {
            val organizationId = param.uuid("organizationId")
            val userId = param.uuid("userId")
            val page = query.long("page", default = 1L)
            val enabled = query.boolean("enabled", default = true)

            text("$organizationId/$userId/$page/$enabled")
        }
    }
}
```

Routes accept named `:id` and `{id}` segments.
`get`, `post`, `put`, `patch`, `delete`, `head`, `options`, and `query` register methods explicitly.
`query(path) { ... }` registers the HTTP QUERY method; `query.long("page")` reads a URL query value.

`param`, `query`, and `header` provide typed extraction.

| Form | Missing value | Invalid value |
| --- | --- | --- |
| `string`, `boolean`, `uuid`, numeric, `instant`, `duration` | 400 | 400 |
| `...OrNull` | `null` | 400 |
| Numeric, Boolean, Instant, or Duration with `default` | Default | 400 |

Unknown paths return 404.
A path with no handler for its method returns 405 with `Allow`.
Request bodies are buffered text and are read with `body.text()`.

## Plugins

Every optional feature is a module and is installed explicitly.

| Module | API | Use |
| --- | --- | --- |
| `koyane-content-negotiation` | `ContentNegotiation` | Content-type selection and codecs |
| `koyane-kotlinx-serialization` | `json`, `receive`, `respond` | `kotlinx.serialization` JSON codec |
| `koyane-cors` | `Cors` | CORS preflight and response headers |
| `koyane-bearer-authentication` | `BearerAuthentication` | Bearer guards with a typed principal |
| `koyane-error-handling` | `ErrorHandling` | Exception and HTTP-failure responses |
| `koyane-logging` | `Logging` | Request, response, and failure logging |
| `koyane-kotlinx-datetime` | Input extensions | `kotlinx-datetime` extraction |
| `koyane-testing` | `testApplication` | In-memory application tests |
| `koyane-workers` | `defineWorker` | Cloudflare Workers Fetch adapter |
| `koyane-workers-d1` | `defineD1Worker` | D1 binding with Kagiroi |

For JSON, add the Content Negotiation and Kotlin serialization modules, then supply serializers at the call site.

```kotlin
val app = koyane {
    install(ContentNegotiation) {
        json()
    }
    install(Http) {
        post("/users") {
            val request = receive(CreateUserRequest.serializer())
            respond(
                CreateUserResponse(id = "123", name = request.name),
                CreateUserResponse.serializer(),
            )
        }
    }
}
```

Koyane does not infer serializers, bind request DTOs, or install plugins automatically.

## Test applications

`koyane-testing` dispatches through the same application and HTTP pipeline without opening a port.

```kotlin
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.text
import dev.quwac.koyane.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HelloTest {
    @Test
    fun hello() = testApplication {
        application {
            install(Http) {
                get("/hello") { text("hello") }
            }
        }

        assertEquals(200, client.get("/hello").status)
    }
}
```

## Cloudflare Workers

`koyane-workers` adapts Fetch requests and responses at the target boundary.
`koyane-workers-d1` creates a Kagiroi `D1Database` from the Worker binding, so application setup can stay in `commonMain`.

The [Workers example](examples/hello-workers/README.md) includes Kotlin/JS and Kotlin/Wasm builds,
local D1 migrations, CORS, Bearer authentication, JSON, and typed D1 CRUD.

## Scope

The current build targets Kotlin/JS and Kotlin/Wasm.
Core and HTTP are independent of Fetch and host bindings.
`koyane-workers` provides the Cloudflare Workers adapter.
The current HTTP model uses buffered text bodies.
Binary or streaming bodies, automatic request binding, OpenAPI generation, client generation,
and a JVM server adapter are not part of this repository.

## Development

```sh
mise trust
mise install --locked java bun node hk pkl lychee gh zizmor betterleaks \
    github:github/gh-actions-lock github:jdx/jactionlint
mise run setup
hk check --all
mise run test
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for IntelliJ setup, focused checks, dependency verification, and CI maintenance.

## License

[MIT](LICENSE)

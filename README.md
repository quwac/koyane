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
Applications are written in common Kotlin and built for Kotlin/JS or Kotlin/Wasm on Cloudflare Workers or Node.js.
Core and HTTP are runtime-independent; adapters connect applications to host runtimes.

The API is experimental.

- [Cheat sheet](docs/CHEATSHEET.md)
- [Workers example](examples/hello-workers/README.md)
- [Lambda Web Functions example](examples/hello-lambda-web/README.md)
- [Contributing](CONTRIBUTING.md)

## Dependencies

`dev.koyane:koyane-bom` aligns the versions of all Koyane library modules.
Import the BOM and add the modules used by your application without individual versions.
The BOM does not add libraries or install plugins automatically.

These snapshot coordinates require [local Maven publication](CONTRIBUTING.md#maven-publications).
This build does not configure a remote Maven repository for publication.
The snippets below belong in a Kotlin Multiplatform build targeting `js`, `wasmJs`, or both.

### Cloudflare Workers dependencies

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform("dev.koyane:koyane-bom:0.0.1-SNAPSHOT"))
            implementation("dev.koyane:koyane-workers")
            // implementation("dev.koyane:koyane-logging")
        }
    }
}
```

### Node.js and Lambda Web Functions dependencies

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform("dev.koyane:koyane-bom:0.0.1-SNAPSHOT"))
            implementation("dev.koyane:koyane-node")
            // implementation("dev.koyane:koyane-logging")
        }
    }
}
```

Both adapters expose HTTP and Core transitively.
For a runtime-independent library, use `dev.koyane:koyane-http` with the same BOM.
Source-based multi-project builds can use `implementation(project(":koyane-http"))` directly.

## Application

Install HTTP explicitly and return an `HttpResponse` from every handler.

```kotlin
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http

val app = koyane {
    install(Http) {
        get("/hello/:name") { text("Hello, ${param.string("name")}!") }
    }
}
```

Optional modules provide content negotiation, explicit JSON codecs, CORS, Bearer authentication,
error handling, logging, date/time inputs, and in-memory application tests.
See the [cheat sheet](docs/CHEATSHEET.md) for API examples and behavior.

## Scope

The current build targets Kotlin/JS and Kotlin/Wasm.
Core and HTTP are independent of Fetch and host bindings.
`koyane-workers` provides the Cloudflare Workers adapter.
`koyane-node` provides the Node.js HTTP adapter, including AWS Lambda Web Functions.
The adapters have no database or driver dependency; applications supply their own database clients.
Applications that need database access are expected to use Kagiroi as their database client.
The current HTTP model uses buffered text bodies.
Binary or streaming bodies, automatic request binding, OpenAPI generation, client generation,
and a JVM server adapter are not part of this repository.

## License

[MIT](LICENSE)

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

### Node.js dependencies

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
`koyane-workers` provides the [Cloudflare Workers](https://developers.cloudflare.com/workers/) adapter.
`koyane-node` provides a standard Node.js HTTP server. Hosting options include:

| Provider | Run directly on Node.js | Run as a container |
| --- | --- | --- |
| AWS | [Lambda Web Functions](https://docs.aws.amazon.com/lambda/latest/dg/web-functions.html), [Amazon EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/concepts.html) | [Amazon ECS](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-task-definition.html) (including Fargate and [Express Mode](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/express-service-overview.html)), [Amazon EKS](https://docs.aws.amazon.com/eks/latest/userguide/kubernetes-concepts.html), [Elastic Beanstalk Docker](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/create_deploy_docker.html), [Lightsail container services](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-container-services.html) |
| Google Cloud | [Compute Engine](https://docs.cloud.google.com/compute/docs/overview) | [Cloud Run](https://docs.cloud.google.com/run/docs/container-contract), [GKE](https://docs.cloud.google.com/kubernetes-engine/docs/deploy-app-cluster), [App Engine flexible custom runtimes](https://docs.cloud.google.com/appengine/docs/flexible/custom-runtimes/build) |
| Microsoft Azure | [Azure Virtual Machines](https://learn.microsoft.com/en-us/azure/virtual-machines/overview) | [Container Apps](https://learn.microsoft.com/en-us/azure/container-apps/ingress-how-to), [App Service custom containers](https://learn.microsoft.com/azure/app-service/configure-custom-container), [AKS](https://learn.microsoft.com/en-us/azure/aks/secure-aks) |
| Oracle Cloud | [OCI Compute](https://docs.oracle.com/en-us/iaas/Content/Compute/Concepts/computeoverview.htm) | [OCI Container Instances](https://docs.oracle.com/en-us/iaas/Content/container-instances/home.htm), [OKE](https://docs.oracle.com/en-us/iaas/Content/ContEng/) |
| Self-hosted | Node.js on virtual machines or physical servers | [Docker](https://docs.docker.com/guides/nodejs/) |

On virtual machines such as Compute Engine, applications run directly on Node.js; containers are optional.
[AWS App Runner](https://docs.aws.amazon.com/apprunner/latest/dg/apprunner-availability-change.html)
is also an option for existing App Runner customers only.

The adapters have no database or driver dependency; applications supply their own database clients.
Applications that need database access are expected to use [Kagiroi](https://github.com/quwac/kagiroi)
as their database client.
The current HTTP model uses buffered text bodies.
Binary or streaming bodies, automatic request binding, OpenAPI generation, client generation,
and a JVM server adapter are not part of this repository.

## License

[MIT](LICENSE)

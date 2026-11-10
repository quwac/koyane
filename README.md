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
Applications use common Kotlin, with Kotlin/JS and Kotlin/Wasm adapters for Fetch hosts, Node.js servers, and HTTP functions.
Core and HTTP are runtime-independent; adapters connect applications to host runtimes.

The API is experimental.

- [Cheat sheet](docs/CHEATSHEET.md)
- [Contributing](CONTRIBUTING.md)

## Supported services

All adapters target Kotlin/JS and Kotlin/Wasm.
Choose your hosting service, then add the module listed below.
Application code uses the same runtime-independent Core and HTTP APIs on every host.

### Functions and edge services

| Service | Runtime used by Koyane | Module | Example |
| --- | --- | --- | --- |
| <img src="https://raw.githubusercontent.com/gilbarbara/logos/22f03ffc4a652309d1fea4ade70f46e9e1d6602c/logos/cloudflare-workers-icon.svg" width="20" height="20" alt=""> [Cloudflare Workers](https://developers.cloudflare.com/workers/) | Workers runtime | `koyane-workers` | [Workers](examples/hello-workers/README.md) |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/aws/compute/lambda.png" width="20" height="20" alt=""> [AWS Lambda Event Functions](https://docs.aws.amazon.com/lambda/latest/dg/nodejs-handler.html) | Node.js | `koyane-lambda` | [Lambda HTTP events](examples/hello-lambda/README.md) |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/aws/compute/lambda.png" width="20" height="20" alt=""> [AWS Lambda Web Functions](https://docs.aws.amazon.com/lambda/latest/dg/web-functions.html) | Node.js | `koyane-node` | [Lambda HTTP server](examples/hello-lambda-web/README.md) |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/gcp/compute/functions.png" width="20" height="20" alt=""> [Google Cloud Run functions](https://docs.cloud.google.com/run/docs/write-functions) | Node.js | `koyane-node` | [Cloud Run functions](examples/hello-cloud-run-functions/README.md) |
| <img src="https://assets.vercel.com/image/upload/front/favicon/vercel/180x180.png" width="20" height="20" alt=""> [Vercel Functions](https://vercel.com/docs/functions/runtimes/node-js) | Node.js | `koyane-fetch` | [Vercel](examples/hello-fetch-functions/README.md#vercel) |
| <img src="https://raw.githubusercontent.com/gilbarbara/logos/22f03ffc4a652309d1fea4ade70f46e9e1d6602c/logos/supabase-icon.svg" width="20" height="20" alt=""> [Supabase Edge Functions](https://supabase.com/docs/guides/functions) | Supabase Edge Runtime (Deno) | `koyane-fetch` | [Supabase](examples/hello-fetch-functions/README.md#supabase) |

The runtime column identifies the runtime used by this implementation; services may offer other runtimes.
Kotlin/Wasm runs through a JavaScript entry point within that runtime.
Lambda Event Functions support API Gateway HTTP API and Function URL payload 2.0.
Cloud Run functions use Google's Node.js Functions Framework.
Compatibility is checked locally; deployment to these cloud services is not verified.

### Virtual machines and container services

The following hosts can run the Node.js HTTP server on either Kotlin target.
On virtual machines such as Compute Engine, run directly on Node.js or use a container.
Container services run the same application packaged as a container image.

| Service | Runtime used by Koyane | Module | Deployment |
| --- | --- | --- | --- |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/aws/compute/ec2.png" width="20" height="20" alt=""> [Amazon EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/concepts.html) | Node.js | `koyane-node` | Directly or in a container |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/gcp/compute/compute-engine.png" width="20" height="20" alt=""> [Google Compute Engine](https://docs.cloud.google.com/compute/docs/overview) | Node.js | `koyane-node` | Directly or in a container |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/azure/compute/vm.png" width="20" height="20" alt=""> [Azure Virtual Machines](https://learn.microsoft.com/en-us/azure/virtual-machines/overview) | Node.js | `koyane-node` | Directly or in a container |
| <picture><source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/oci/compute/vm-white.png"><img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/oci/compute/vm.png" width="20" height="20" alt=""></picture> [OCI Compute](https://docs.oracle.com/en-us/iaas/Content/Compute/Concepts/computeoverview.htm) | Node.js | `koyane-node` | Directly or in a container |
| <img src="https://api.iconify.design/lucide/server.svg?color=%237d8590" width="20" height="20" alt=""> Self-hosted virtual machines or physical servers | Node.js | `koyane-node` | Directly or in a container, such as [Docker](https://docs.docker.com/guides/nodejs/) |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/aws/compute/elastic-container-service.png" width="20" height="20" alt=""> [Amazon ECS](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-task-definition.html), including Fargate and [Express Mode](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/express-service-overview.html) | Node.js | `koyane-node` | Container |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/aws/compute/elastic-kubernetes-service.png" width="20" height="20" alt=""> [Amazon EKS](https://docs.aws.amazon.com/eks/latest/userguide/kubernetes-concepts.html) | Node.js | `koyane-node` | Container |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/aws/compute/elastic-beanstalk.png" width="20" height="20" alt=""> [AWS Elastic Beanstalk Docker](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/create_deploy_docker.html) | Node.js | `koyane-node` | Container |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/aws/compute/lightsail.png" width="20" height="20" alt=""> [Amazon Lightsail container services](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-container-services.html) | Node.js | `koyane-node` | Container |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/gcp/compute/run.png" width="20" height="20" alt=""> [Google Cloud Run](https://docs.cloud.google.com/run/docs/container-contract) | Node.js | `koyane-node` | Container |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/gcp/compute/kubernetes-engine.png" width="20" height="20" alt=""> [Google Kubernetes Engine (GKE)](https://docs.cloud.google.com/kubernetes-engine/docs/deploy-app-cluster) | Node.js | `koyane-node` | Container |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/gcp/compute/app-engine.png" width="20" height="20" alt=""> [Google App Engine flexible custom runtimes](https://docs.cloud.google.com/appengine/docs/flexible/custom-runtimes/build) | Node.js | `koyane-node` | Container |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/azure/compute/container-apps.png" width="20" height="20" alt=""> [Azure Container Apps](https://learn.microsoft.com/en-us/azure/container-apps/ingress-how-to) | Node.js | `koyane-node` | Container |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/azure/appservices/app-services.png" width="20" height="20" alt=""> [Azure App Service custom containers](https://learn.microsoft.com/azure/app-service/configure-custom-container) | Node.js | `koyane-node` | Container |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/azure/compute/kubernetes-services.png" width="20" height="20" alt=""> [Azure Kubernetes Service (AKS)](https://learn.microsoft.com/en-us/azure/aks/secure-aks) | Node.js | `koyane-node` | Container |
| <picture><source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/oci/compute/container-white.png"><img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/oci/compute/container.png" width="20" height="20" alt=""></picture> [OCI Container Instances](https://docs.oracle.com/en-us/iaas/Content/container-instances/home.htm) | Node.js | `koyane-node` | Container |
| <picture><source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/oci/compute/oke-white.png"><img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/oci/compute/oke.png" width="20" height="20" alt=""></picture> [Oracle Kubernetes Engine (OKE)](https://docs.oracle.com/en-us/iaas/Content/ContEng/) | Node.js | `koyane-node` | Container |
| <img src="https://raw.githubusercontent.com/mingrammer/diagrams/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources/aws/compute/app-runner.png" width="20" height="20" alt=""> [AWS App Runner](https://docs.aws.amazon.com/apprunner/latest/dg/apprunner-availability-change.html) | Node.js | `koyane-node` | Container; existing App Runner customers only |

These are hosting options for the Node.js server; service-specific deployment configurations are not included.

Icon sources: [Diagrams](https://github.com/mingrammer/diagrams/tree/5a9e8af2d4e48ae2bbd002a4c0ca714d5b9219e1/resources),
[SVG Logos](https://github.com/gilbarbara/logos/tree/22f03ffc4a652309d1fea4ade70f46e9e1d6602c/logos),
[Vercel](https://assets.vercel.com/image/upload/front/favicon/vercel/180x180.png),
and [Lucide via Iconify](https://icon-sets.iconify.design/lucide/).

## Dependencies

`dev.koyane:koyane-bom` aligns the versions of all Koyane library modules.
Import the BOM and add the modules used by your application without individual versions.
The BOM does not add libraries or install plugins automatically.

These snapshot coordinates require [local Maven publication](CONTRIBUTING.md#maven-publications).
This build does not configure a remote Maven repository for publication.
The snippet below belongs in a Kotlin Multiplatform build targeting `js`, `wasmJs`, or both.

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform("dev.koyane:koyane-bom:0.0.1-SNAPSHOT"))
            implementation("dev.koyane:koyane-workers") // Choose the module for your service above.
            // implementation("dev.koyane:koyane-logging")
        }
    }
}
```

All adapters expose HTTP and Core transitively.
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

Core and HTTP are independent of Fetch and host bindings.
CloudEvents, SQS events, API Gateway payload 1.0, and ALB events are outside the HTTP adapters' scope.

The adapters have no database or driver dependency; applications supply their own database clients.
Applications that need database access are expected to use [Kagiroi](https://github.com/quwac/kagiroi)
as their database client.
The current HTTP model uses buffered text bodies.
Binary or streaming bodies, automatic request binding, OpenAPI generation, client generation,
and a JVM server adapter are not part of this repository.

## License

[MIT](LICENSE)

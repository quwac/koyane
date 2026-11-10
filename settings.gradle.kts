pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "koyane"
include(
    ":koyane-bom",
    ":koyane-core",
    ":koyane-http",
    ":koyane-content-negotiation",
    ":koyane-bearer-authentication",
    ":koyane-cors",
    ":koyane-error-handling",
    ":koyane-kotlinx-datetime",
    ":koyane-kotlinx-serialization",
    ":koyane-logging",
    ":koyane-testing",
    ":koyane-workers",
    ":koyane-node",
    ":koyane-fetch",
    ":koyane-lambda",
    ":examples:hello-fetch-functions",
    ":examples:hello-cloud-run-functions",
    ":examples:hello-lambda",
    ":examples:hello-workers",
    ":examples:hello-lambda-web",
)

if (providers.gradleProperty("compileFixture").isPresent) {
    include(":compile-tests")
}

@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
}

val fixture = providers.gradleProperty("compileFixture").get()
require(fixture.matches(Regex("[A-Za-z]+")))
require(file("fixtures/$fixture").isDirectory) { "Unknown compile fixture: $fixture" }

kotlin {
    wasmJs {
        nodejs()
        compilerOptions {
            freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
        }
    }
    sourceSets {
        commonMain {
            kotlin.srcDirs("contracts", "fixtures/$fixture")
            dependencies {
                implementation(project(":koyane-cloudflare-workers"))
                implementation(project(":koyane-node"))
                implementation(project(":koyane-lambda"))
                implementation(project(":koyane-bearer-authentication"))
                implementation(project(":koyane-content-negotiation"))
                implementation(project(":koyane-cors"))
                implementation(project(":koyane-error-handling"))
                implementation(project(":koyane-http"))
                implementation(project(":koyane-kotlinx-serialization"))
                implementation(project(":koyane-logging"))
            }
        }
    }
}

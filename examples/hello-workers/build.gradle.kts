@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

val cloudflareWorkerFixture = providers.gradleProperty("cloudflareWorkerFixture").isPresent

val kagiroiVersion = providers.gradleProperty("kagiroiVersion").get()

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    js {
        nodejs()
        useEsModules()
        binaries.executable()
    }
    wasmJs {
        nodejs()
        binaries.executable()
        generateTypeScriptDefinitions()
    }
    sourceSets {
        if (cloudflareWorkerFixture) {
            commonMain { kotlin.setSrcDirs(listOf("src/cloudflareWorkerTest/kotlin")) }
            jsMain { kotlin.setSrcDirs(emptyList<String>()) }
            wasmJsMain { kotlin.setSrcDirs(emptyList<String>()) }
        }
        commonMain.dependencies {
            implementation("dev.quwac.kagiroi:kagiroi-sql:$kagiroiVersion")
            implementation("dev.quwac.kagiroi:kagiroi-d1-driver:$kagiroiVersion")
            implementation(project(":koyane-bearer-authentication"))
            implementation(project(":koyane-content-negotiation"))
            implementation(project(":koyane-cors"))
            implementation(project(":koyane-error-handling"))
            implementation(project(":koyane-http"))
            implementation(project(":koyane-kotlinx-serialization"))
            implementation(project(":koyane-logging"))
            implementation(project(":koyane-cloudflare-workers"))
        }
    }
}

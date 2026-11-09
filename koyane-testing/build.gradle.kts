@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
}

kotlin {
    js {
        nodejs()
    }
    wasmJs {
        nodejs()
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":koyane-http"))
            api("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        commonTest.dependencies {
            implementation(project(":koyane-bearer-authentication"))
            implementation(project(":koyane-content-negotiation"))
            implementation(project(":koyane-cors"))
            implementation(project(":koyane-error-handling"))
            implementation(kotlin("test"))
        }
    }
}

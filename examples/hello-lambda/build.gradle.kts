@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
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
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":koyane-lambda"))
            implementation(project(":koyane-error-handling"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
    }
}

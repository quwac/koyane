@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
}

kotlin {
    js {
        nodejs()
        useEsModules()
    }
    wasmJs {
        nodejs()
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":koyane-http"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

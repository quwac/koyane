@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

val kagiroiVersion = providers.gradleProperty("kagiroiVersion").get()

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
            api("dev.quwac.kagiroi:kagiroi-d1-driver:$kagiroiVersion")
            api(project(":koyane-core"))
        }
        jsMain.dependencies {
            implementation(project(":koyane-workers"))
        }
        wasmJsMain.dependencies {
            implementation(project(":koyane-workers"))
        }
    }
}

plugins {
    kotlin("multiplatform") version "2.4.21" apply false
    kotlin("plugin.serialization") version "2.4.21" apply false
    id("dev.detekt") version "2.0.0-alpha.6"
}

allprojects {
    group = "dev.koyane"
    version = "0.0.1-SNAPSHOT"
}

dependencies {
    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.6")
}

detekt {
    toolVersion = "2.0.0-alpha.6"
    source.setFrom(
        files("build.gradle.kts", "settings.gradle.kts"),
        subprojects.filter { it.path != ":compile-tests" }.map {
            files(it.layout.projectDirectory.dir("src"), it.buildFile)
        },
    )
    config.setFrom(layout.projectDirectory.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    basePath.set(rootDir)
}

tasks.named<dev.detekt.gradle.Detekt>("detekt") {
    include("**/*.kt", "**/*.kts")
}

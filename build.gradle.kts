import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("multiplatform") version "2.4.21" apply false
    kotlin("plugin.serialization") version "2.4.21" apply false
    id("dev.detekt") version "2.0.0-alpha.6"
}

allprojects {
    group = "dev.koyane"
    version = "0.0.1-SNAPSHOT"
}

subprojects {
    if (path.startsWith(":koyane-") && name != "koyane-bom") {
        pluginManager.apply("maven-publish")
    }
    pluginManager.withPlugin("maven-publish") {
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set(project.name)
                    description.set("Typed Kotlin Multiplatform applications for JavaScript and WebAssembly.")
                    url.set("https://github.com/quwac/koyane")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/license/mit")
                            distribution.set("repo")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/quwac/koyane.git")
                        developerConnection.set("scm:git:ssh://git@github.com/quwac/koyane.git")
                        url.set("https://github.com/quwac/koyane")
                    }
                }
            }
        }
    }
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

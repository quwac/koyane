import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    kotlin("multiplatform") version "2.4.21" apply false
    kotlin("plugin.serialization") version "2.4.21" apply false
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
    id("dev.detekt") version "2.0.0-alpha.6"
}

val publishedProjects = setOf(
    ":koyane-bearer-authentication",
    ":koyane-bom",
    ":koyane-content-negotiation",
    ":koyane-core",
    ":koyane-cors",
    ":koyane-error-handling",
    ":koyane-fetch",
    ":koyane-http",
    ":koyane-kotlinx-datetime",
    ":koyane-kotlinx-serialization",
    ":koyane-lambda",
    ":koyane-logging",
    ":koyane-node",
    ":koyane-testing",
    ":koyane-workers",
)

allprojects {
    group = "dev.quwac.koyane"
    version = providers.gradleProperty("version").getOrElse("0.0.1-SNAPSHOT")
}

subprojects {
    if (path in publishedProjects) {
        apply(plugin = "com.vanniktech.maven.publish")
        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral()
            if (providers.gradleProperty("signingInMemoryKey").isPresent) {
                signAllPublications()
            }
            coordinates(project.group.toString(), project.name, project.version.toString())
            pom {
                name.set("Koyane ${project.name}")
                description.set("Typed Kotlin Multiplatform applications for JavaScript and WebAssembly.")
                inceptionYear.set("2026")
                url.set("https://github.com/quwac/koyane")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/mit")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("quwac")
                        name.set("quwabara")
                        url.set("https://github.com/quwac")
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

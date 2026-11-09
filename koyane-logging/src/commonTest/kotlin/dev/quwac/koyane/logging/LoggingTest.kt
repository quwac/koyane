package dev.quwac.koyane.logging

import dev.quwac.koyane.core.Plugin
import dev.quwac.koyane.core.PluginScope
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpRequest
import dev.quwac.koyane.http.HttpStatus
import dev.quwac.koyane.http.empty
import dev.quwac.koyane.http.fetch
import dev.quwac.koyane.http.onError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoggingTest {
    @Test
    fun logsActualRequestsAndFrameworkResponsesWithoutQueries() {
        val messages = mutableListOf<String>()
        lateinit var configuration: LoggingConfiguration
        val app = koyane {
            install(Logging) {
                configuration = this
                sink = { messages += it }
            }
            install(Http) { get("/hello/{id}") { text(param.int("id").toString()) } }
        }
        configuration.sink = { error("installed configuration must be a snapshot") }
        for ((request, status, path) in listOf(
            Triple(HttpRequest("GET", "https://example.test/hello/1?token=synthetic"), 200, "/hello/1"),
            Triple(HttpRequest("POST", "/hello/1"), 405, "/hello/1"),
            Triple(HttpRequest("GET", "/hello/no"), 400, "/hello/no"),
            Triple(HttpRequest("GET", "https://example.test?query=hidden"), 404, "/"),
        )) {
            messages.clear()
            assertEquals(status, runImmediately { app.fetch(request) }.status)
            assertEquals(
                if (status == 200) {
                    listOf("Request ${request.method} $path", "Response ${request.method} $path $status")
                } else {
                    listOf(
                        "Request ${request.method} $path",
                        "Error ${request.method} $path: HTTP $status",
                        "Response ${request.method} $path $status",
                    )
                },
                messages,
            )
        }
        messages.clear()
        app[Logging].log("application message")
        assertEquals(listOf("application message"), messages)
    }

    @Test
    fun logsFailuresAndRecoveredResponses() {
        val messages = mutableListOf<String>()
        val recovery = object : Plugin<Unit, Unit> {
            override fun createConfiguration() = Unit
            override fun install(configuration: Unit) = Unit
            override fun onInstall(scope: PluginScope, extension: Unit) {
                scope.onError { empty(HttpStatus.INTERNAL_SERVER_ERROR) }
            }
        }
        val app = koyane {
            install(Http) { get("/") { error("failed") } }
            install(Logging) { sink = { messages += it } }
            install(recovery)
        }
        assertEquals(500, runImmediately { app.fetch(HttpRequest("GET", "/")) }.status)
        assertEquals(listOf("Request GET /", "Error GET /: failed", "Response GET / 500"), messages)
        messages.clear()
        val unhandled = koyane {
            install(Http) { get("/") { error("unhandled") } }
            install(Logging) { sink = { messages += it } }
        }
        assertFailsWith<IllegalStateException> { runImmediately { unhandled.fetch(HttpRequest("GET", "/")) } }
        assertEquals(listOf("Request GET /", "Error GET /: unhandled"), messages)
    }

    @Test
    fun ownsItsSinkPerApplicationAndRejectsDuplicateInstallation() {
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        fun create(messages: MutableList<String>) = koyane {
            install(Logging) { sink = { messages += it } }
            install(Http) { get("/") { text("ok") } }
        }
        val firstApp = create(first)
        val secondApp = create(second)
        runImmediately { firstApp.fetch(HttpRequest("GET", "/")) }
        assertEquals(emptyList(), second)
        runImmediately { secondApp.fetch(HttpRequest("GET", "/")) }
        assertEquals(listOf("Request GET /", "Response GET / 200"), first)
        assertEquals(first, second)
        assertFailsWith<IllegalStateException> {
            koyane {
                install(Logging)
                install(Logging)
            }
        }
    }
}

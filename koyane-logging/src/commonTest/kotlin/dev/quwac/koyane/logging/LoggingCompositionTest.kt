package dev.quwac.koyane.logging

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpRequest
import dev.quwac.koyane.http.HttpResponse
import dev.quwac.koyane.http.fetch
import dev.quwac.koyane.http.onError
import dev.quwac.koyane.http.onResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class LoggingCompositionTest {
    @Test
    fun loggingObservesTheFinalStatusRegardlessOfInstallationOrder() {
        for (loggingFirst in listOf(false, true)) {
            val logs = mutableListOf<String>()
            val app = koyane {
                if (loggingFirst) install(Logging) { sink = { logs += it } }
                install(Hooks { onResponse { it.copy(status = 503) } })
                if (!loggingFirst) install(Logging) { sink = { logs += it } }
                install(Http) { get("/") { text("ok") } }
            }
            assertEquals(503, runImmediately { app.fetch(HttpRequest("GET", "/")) }.status)
            assertEquals(listOf("Request GET /", "Response GET / 503"), logs)
        }
    }

    @Test
    fun reappliesHealthyTransformsAndLogsOnlyTheFinalRecoveredResponse() {
        val logs = mutableListOf<String>()
        var corsCalls = 0
        var failures = 0
        val app = koyane {
            install(Logging) { sink = { logs += it } }
            install(
                Hooks {
                    onResponse {
                        corsCalls++
                        it.copy(headers = it.headers + ("Access-Control-Allow-Origin" to "https://example.test"))
                    }
                },
            )
            install(
                Hooks {
                    onResponse {
                        failures++
                        error("encoder failed")
                    }
                },
            )
            install(
                Hooks {
                    onResponse { it.copy(headers = it.headers + ("X-Request-Id" to "id")) }
                    onError { HttpResponse(500) }
                },
            )
            install(Http) { get("/") { text("ok") } }
        }
        repeat(2) { iteration ->
            logs.clear()
            val response = runImmediately { app.fetch(HttpRequest("GET", "/")) }
            assertEquals(500, response.status)
            assertEquals("https://example.test", response.headers["Access-Control-Allow-Origin"])
            assertEquals("id", response.headers["X-Request-Id"])
            assertEquals((iteration + 1) * 2, corsCalls)
            assertEquals(iteration + 1, failures)
            assertEquals(listOf("Request GET /", "Error GET /: encoder failed", "Response GET / 500"), logs)
        }
    }
}

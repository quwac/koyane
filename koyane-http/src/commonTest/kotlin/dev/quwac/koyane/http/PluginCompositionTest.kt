package dev.quwac.koyane.http

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.core.AttributeKey
import dev.quwac.koyane.core.Plugin
import dev.quwac.koyane.core.PluginScope
import dev.quwac.koyane.core.koyane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PluginCompositionTest {
    @Test
    fun directHttpInstallationHandlesRequestsWithAnIsolatedPersistentContext() {
        val key = AttributeKey<Int>("count")
        val configuration = Http.createConfiguration().apply {
            get("/") {
                val count = (application.attributes.getOrNull(key) ?: 0) + 1
                application.attributes[key] = count
                assertNull(attributes.getOrNull(key))
                attributes[key] = count
                text(count.toString())
            }
        }
        val first = Http.install(configuration)
        val second = Http.install(configuration)
        assertEquals("1", runImmediately { first.handle(HttpRequest("GET", "/")) }.body)
        assertEquals("2", runImmediately { first.handle(HttpRequest("GET", "/")) }.body)
        assertEquals("1", runImmediately { second.handle(HttpRequest("GET", "/")) }.body)
        assertEquals(404, runImmediately { first.handle(HttpRequest("GET", "/missing")) }.status)
    }

    @Test
    fun normalHttpInstallationUsesTheOwningApplication() {
        var observed: Application? = null
        val app = koyane {
            install(Http) {
                get("/") {
                    observed = application
                    text("ok")
                }
            }
        }
        assertEquals("ok", runImmediately { app.fetch(HttpRequest("GET", "/")) }.body)
        assertSame(app, observed)
    }

    @Test
    fun requestIdsPreserveShortCircuitResponsesRegardlessOfInstallationOrder() {
        for (idsFirst in listOf(false, true)) {
            var generated = 0
            val app = koyane {
                if (idsFirst) install(RequestIds) { nextId = { "id-${++generated}" } }
                install(Hooks { onRequest { HttpResponse(401) } })
                if (!idsFirst) install(RequestIds) { nextId = { "id-${++generated}" } }
                install(Http) { get("/") { error("handler must be skipped") } }
            }
            val response = runImmediately { app.fetch(HttpRequest("GET", "/")) }
            assertEquals(401, response.status)
            assertEquals(if (idsFirst) "id-1" else null, response.headers["X-Request-Id"])
            assertEquals(if (idsFirst) 1 else 0, generated)
        }
    }

    @Test
    fun requestIdsPreserveRecoveryWhenInitializationFails() {
        val app = koyane {
            install(RequestIds) { nextId = { error("id generation failed") } }
            install(Hooks { onError { HttpResponse(503) } })
            install(Http) { get("/") { error("handler must be skipped") } }
        }
        val response = runImmediately { app.fetch(HttpRequest("GET", "/")) }
        assertEquals(503, response.status)
        assertNull(response.headers["X-Request-Id"])
    }
}

private val requestId = AttributeKey<String>("request id")
private class RequestIdsConfiguration(var nextId: () -> String = { "anonymous" })

private object RequestIds : Plugin<RequestIdsConfiguration, () -> String> {
    override fun createConfiguration() = RequestIdsConfiguration()
    override fun install(configuration: RequestIdsConfiguration) = configuration.nextId
    override fun onInstall(scope: PluginScope, extension: () -> String) {
        scope.onRequest {
            attributes[requestId] = extension()
            null
        }
        scope.beforeHandler {
            val id: String = attributes[requestId]
            check(id.isNotEmpty())
            null
        }
        scope.onResponse { response ->
            val id = attributes.getOrNull(requestId)
            if (id == null) response else response.copy(headers = response.headers + ("X-Request-Id" to id))
        }
    }
}

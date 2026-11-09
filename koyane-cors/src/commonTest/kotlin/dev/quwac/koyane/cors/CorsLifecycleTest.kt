package dev.quwac.koyane.cors

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpResponse
import dev.quwac.koyane.http.onError
import dev.quwac.koyane.http.onRequest
import dev.quwac.koyane.http.onResponse
import dev.quwac.koyane.logging.Logging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class CorsLifecycleTest {
    @Test
    fun decoratesFrameworkAndEarlyResponsesEvenWhenItsRequestHookWasSkipped() {
        for (corsFirst in listOf(false, true)) {
            val app = koyane {
                if (corsFirst) install(Cors) { allowOrigin(CLIENT_ORIGIN) }
                install(Hooks { onRequest { HttpResponse(401) } })
                if (!corsFirst) install(Cors) { allowOrigin(CLIENT_ORIGIN) }
                install(Http) { get("/") { error("must be skipped") } }
            }
            val response = app.corsFetch(mapOf("Origin" to CLIENT_ORIGIN))
            assertEquals(401, response.status)
            assertEquals(CLIENT_ORIGIN, response.headers["Access-Control-Allow-Origin"])
        }
        val app = corsApplication { allowOrigin(CLIENT_ORIGIN) }
        for ((method, path, status) in listOf(
            Triple("GET", "/%GG", 400),
            Triple("GET", "/missing", 404),
            Triple("PUT", "/", 405),
        )) {
            val response = app.corsFetch(mapOf("Origin" to CLIENT_ORIGIN), method, path)
            assertEquals(status, response.status)
            assertEquals(CLIENT_ORIGIN, response.headers["Access-Control-Allow-Origin"])
        }
    }

    @Test
    fun decoratesRecoveredHandlerAndResponseFailuresInEitherInstallationOrder() {
        for (corsFirst in listOf(false, true)) {
            for (responseFailure in listOf(false, true)) {
                val app = koyane {
                    if (corsFirst) {
                        install(Cors) {
                            allowOrigin(CLIENT_ORIGIN)
                            allowCredentials = true
                        }
                    }
                    install(
                        Hooks {
                            if (responseFailure) onResponse { error("response failed") }
                            onError { HttpResponse(500) }
                        },
                    )
                    install(Http) { get("/") { if (responseFailure) text("ok") else error("handler failed") } }
                    if (!corsFirst) {
                        install(Cors) {
                            allowOrigin(CLIENT_ORIGIN)
                            allowCredentials = true
                        }
                    }
                }
                val response = app.corsFetch(mapOf("Origin" to CLIENT_ORIGIN))
                assertEquals(500, response.status)
                assertEquals(CLIENT_ORIGIN, response.headers["Access-Control-Allow-Origin"])
                assertEquals("true", response.headers["Access-Control-Allow-Credentials"])
                assertEquals("Origin", response.headers["Vary"])
            }
        }
    }

    @Test
    fun preservesUnhandledFailuresWithoutInventingAnErrorResponse() {
        val failure = IllegalStateException("failure")
        val app = koyane {
            install(Cors) { allowOrigin(CLIENT_ORIGIN) }
            install(Http) { get("/") { throw failure } }
        }
        assertSame(failure, assertFailsWith<IllegalStateException> { app.corsFetch(mapOf("Origin" to CLIENT_ORIGIN)) })
    }

    @Test
    fun mergesVaryCaseInsensitivelyAndPreservesWildcard() {
        for ((headers, expected) in listOf(
            mapOf("Vary" to "Accept-Encoding, origin", "vary" to "Accept-Language, ORIGIN") to
                "Accept-Encoding, origin, Accept-Language",
            mapOf("vArY" to "*") to "*",
        )) {
            val app = koyane {
                install(Cors) { allowOrigin(CLIENT_ORIGIN) }
                install(Http) { get("/") { HttpResponse(200, headers = headers) } }
            }
            val response = app.corsFetch()
            assertEquals(expected, response.headers["Vary"])
            assertEquals(1, response.headers.keys.count { it.equals("vary", ignoreCase = true) })
        }
    }

    @Test
    fun removesStaleGrantsAndReplacesCaseVariantsWithoutLosingOtherHeaders() {
        val app = koyane {
            install(Cors) { allowOrigin(CLIENT_ORIGIN) }
            install(Http) {
                get("/") { HttpResponse(200, headers = staleGrants + ("X-Other" to "keep")) }
            }
        }
        for (headers in listOf(
            emptyMap(),
            mapOf("Origin" to "https://other.example"),
            mapOf("Origin" to CLIENT_ORIGIN, "origin" to CLIENT_ORIGIN),
        )) {
            val response = app.corsFetch(headers)
            assertNull(response.headers.keys.firstOrNull { it.startsWith("Access-Control-", ignoreCase = true) })
            assertEquals("keep", response.headers["X-Other"])
        }
        val allowed = app.corsFetch(mapOf("Origin" to CLIENT_ORIGIN))
        assertEquals(CLIENT_ORIGIN, allowed.headers["Access-Control-Allow-Origin"])
        assertEquals(1, allowed.headers.keys.count { it.equals("Access-Control-Allow-Origin", ignoreCase = true) })
        assertNull(allowed.headers["Access-Control-Allow-Credentials"])
        assertNull(allowed.headers["Access-Control-Allow-Headers"])
    }

    @Test
    fun doesNotTurnAnEarlierPreflightErrorIntoAPermissionGrant() {
        val app = koyane {
            install(Hooks { onRequest { HttpResponse(401) } })
            install(Cors) {
                allowOrigin(CLIENT_ORIGIN)
                allowCredentials = true
            }
            install(Http) {}
        }
        val response = app.corsFetch(preflightHeaders(), "OPTIONS")
        assertEquals(401, response.status)
        assertNull(response.headers["Access-Control-Allow-Origin"])
    }

    @Test
    fun loggingObservesTheActualPreflightStatus() {
        val logs = mutableListOf<String>()
        val app = koyane {
            install(Logging) { sink = { logs += it } }
            install(Cors) { allowOrigin(CLIENT_ORIGIN) }
            install(Http) {}
        }
        assertEquals(204, app.corsFetch(preflightHeaders(), "OPTIONS").status)
        assertEquals(listOf("Request OPTIONS /", "Response OPTIONS / 204"), logs)
    }
}

private val staleGrants = mapOf(
    "access-control-allow-origin" to "*",
    "Access-Control-Allow-Origin" to "https://stale.example",
    "ACCESS-CONTROL-ALLOW-CREDENTIALS" to "true",
    "access-control-allow-methods" to "DELETE",
    "access-control-allow-headers" to "*",
    "access-control-expose-headers" to "*",
    "access-control-max-age" to "9999",
)

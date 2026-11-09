package dev.quwac.koyane.cors

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpMethod
import dev.quwac.koyane.http.HttpRequest
import dev.quwac.koyane.http.fetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal const val CLIENT_ORIGIN = "https://client.example"

class CorsTest {
    @Test
    fun deniesSharingByDefaultWithoutBlockingOrdinaryRequests() {
        val app = corsApplication()
        assertEquals(200, app.corsFetch().status)
        assertNull(app.corsFetch(mapOf("Origin" to CLIENT_ORIGIN)).headers["Access-Control-Allow-Origin"])
        assertEquals(403, app.corsFetch(preflightHeaders(), "OPTIONS").status)
    }

    @Test
    fun grantsOnlyTheExactConfiguredOriginAndPreservesResponses() {
        val app = corsApplication { allowOrigin(CLIENT_ORIGIN) }
        val allowed = app.corsFetch(mapOf("oRiGiN" to CLIENT_ORIGIN))
        assertEquals("ok", allowed.body)
        assertEquals(CLIENT_ORIGIN, allowed.headers["Access-Control-Allow-Origin"])
        assertEquals("text/plain; charset=utf-8", allowed.headers["Content-Type"])
        for (origin in listOf(
            "https://other.example",
            "https://client.example.evil",
            "http://client.example",
            "https://client.example:8443",
            "null",
            "https://CLIENT.example",
            CLIENT_ORIGIN + "/",
        )) {
            val response = app.corsFetch(mapOf("Origin" to origin))
            assertEquals(200, response.status)
            assertNull(response.headers["Access-Control-Allow-Origin"], origin)
            assertEquals("Origin", response.headers["Vary"])
        }
        assertNull(app.corsFetch().headers["Access-Control-Allow-Origin"])
        assertEquals("Origin", app.corsFetch().headers["Vary"])
    }

    @Test
    fun supportsExplicitCredentialsAndExposedHeadersOnActualResponses() {
        val app = corsApplication {
            allowOrigin(CLIENT_ORIGIN)
            allowCredentials = true
            exposeHeader("X-Request-Id")
            exposeHeader("x-request-id")
            maxAgeSeconds = 600
        }
        val response = app.corsFetch(mapOf("Origin" to CLIENT_ORIGIN))
        assertEquals("true", response.headers["Access-Control-Allow-Credentials"])
        assertEquals("x-request-id", response.headers["Access-Control-Expose-Headers"])
        assertNull(response.headers["Access-Control-Allow-Methods"])
        assertNull(response.headers["Access-Control-Max-Age"])
        val preflight = app.corsFetch(preflightHeaders(), "OPTIONS")
        assertEquals("true", preflight.headers["Access-Control-Allow-Credentials"])
        assertEquals("600", preflight.headers["Access-Control-Max-Age"])
        assertNull(preflight.headers["Access-Control-Expose-Headers"])
    }

    @Test
    fun supportsWildcardWithoutCredentialsIncludingOpaqueOrigins() {
        val app = corsApplication { allowAnyOrigin() }
        for (origin in listOf(CLIENT_ORIGIN, "https://other.example", "null")) {
            val response = app.corsFetch(mapOf("Origin" to origin))
            assertEquals("*", response.headers["Access-Control-Allow-Origin"])
            assertNull(response.headers["Access-Control-Allow-Credentials"])
        }
        assertNull(
            app.corsFetch(mapOf("Origin" to "https://a.example, https://b.example"))
                .headers["Access-Control-Allow-Origin"],
        )
    }

    @Test
    fun handlesPreflightWithoutRequiringAnOptionsRouteOrExecutingTheHandler() {
        var invocations = 0
        val app = koyane {
            install(Cors) {
                allowOrigin(CLIENT_ORIGIN)
                allowMethod(HttpMethod.PATCH)
                allowHeader("Authorization")
                allowHeader("Content-Type")
                maxAgeSeconds = 300
            }
            install(Http) {
                options("/") {
                    invocations++
                    text("must not run")
                }
            }
        }
        val response = app.corsFetch(
            preflightHeaders(method = "PATCH", headers = "Authorization, CONTENT-TYPE"),
            "OPTIONS",
        )
        assertEquals(204, response.status)
        assertEquals("", response.body)
        assertEquals(0, invocations)
        assertEquals(CLIENT_ORIGIN, response.headers["Access-Control-Allow-Origin"])
        assertEquals("authorization, content-type", response.headers["Access-Control-Allow-Headers"])
        assertEquals(
            setOf("GET", "HEAD", "POST", "PATCH"),
            response.headers["Access-Control-Allow-Methods"]?.split(", ")?.toSet(),
        )
        assertEquals("300", response.headers["Access-Control-Max-Age"])
        assertEquals(204, app.corsFetch(preflightHeaders(), "OPTIONS", "/unknown").status)
    }

    @Test
    fun rejectsPreflightMethodsHeadersAndOriginsOutsideThePolicy() {
        var handlers = 0
        val app = koyane {
            install(Cors) { allowOrigin(CLIENT_ORIGIN) }
            install(Http) {
                options("/") {
                    handlers++
                    text("options")
                }
            }
        }
        for (headers in listOf(
            preflightHeaders(origin = "https://other.example"),
            preflightHeaders(method = "DELETE"),
            preflightHeaders(method = "get"),
            preflightHeaders(method = "CUSTOM"),
            preflightHeaders(headers = "Authorization"),
            preflightHeaders(headers = "Content-Type"),
        )) {
            val response = app.corsFetch(headers, "OPTIONS")
            assertEquals(403, response.status)
            assertNull(response.headers["Access-Control-Allow-Origin"])
            assertNull(response.headers["Access-Control-Allow-Methods"])
            assertEquals(
                setOf("Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"),
                response.headers["Vary"]?.split(", ")?.toSet(),
            )
        }
        assertEquals(0, handlers)
    }

    @Test
    fun validatesPreflightHeaderSyntaxAndAmbiguousSingletonHeaders() {
        val app = corsApplication {
            allowAnyOrigin()
            allowHeader("X-Test")
        }
        for (headers in listOf(
            preflightHeaders(
                origin = "",
            ),
            preflightHeaders(origin = "https://a.example https://b.example"),
            preflightHeaders(method = ""), preflightHeaders(method = "GET, POST"),
            preflightHeaders(headers = "X-Test,"), preflightHeaders(headers = "X-Test\r\nInjected: yes"),
            preflightHeaders(headers = ""), preflightHeaders() + ("origin" to CLIENT_ORIGIN),
            preflightHeaders() + ("access-control-request-method" to "POST"),
        )) {
            assertEquals(400, app.corsFetch(headers, "OPTIONS").status, headers.toString())
        }
    }

    @Test
    fun acceptsCaseInsensitiveHeaderListsWithWhitespaceAndDuplicates() {
        val app = corsApplication {
            allowOrigin(CLIENT_ORIGIN)
            allowHeader("X-Test")
        }
        val headers = preflightHeaders(headers = " x-test, X-TEST\t") +
            ("access-control-request-headers" to "X-Test")
        val response = app.corsFetch(headers, "OPTIONS")
        assertEquals(204, response.status)
        assertEquals("x-test", response.headers["Access-Control-Allow-Headers"])
        assertEquals("0", response.headers["Access-Control-Max-Age"])
    }

    @Test
    fun leavesOrdinaryOptionsAndNonPreflightMethodsToRouting() {
        val app = corsApplication { allowOrigin(CLIENT_ORIGIN) }
        assertEquals("options", app.corsFetch(mapOf("Origin" to CLIENT_ORIGIN), "OPTIONS").body)
        assertEquals("options", app.corsFetch(mapOf("Access-Control-Request-Method" to "POST"), "OPTIONS").body)
        assertEquals("delete", app.corsFetch(mapOf("Origin" to CLIENT_ORIGIN), "DELETE").body)
        assertEquals(405, app.corsFetch(mapOf("Origin" to CLIENT_ORIGIN), "PUT").status)
    }
}

internal fun corsApplication(configure: CorsConfiguration.() -> Unit = {}): Application = koyane {
    install(Cors, configure)
    install(Http) {
        get("/") { text("ok") }
        options("/") { text("options") }
        delete("/") { text("delete") }
    }
}

internal fun Application.corsFetch(
    headers: Map<String, String> = emptyMap(),
    method: String = "GET",
    path: String = "/",
) = runImmediately { fetch(HttpRequest(method, path, headers)) }

internal fun preflightHeaders(origin: String = CLIENT_ORIGIN, method: String = "POST", headers: String? = null) =
    buildMap {
        put("Origin", origin)
        put("Access-Control-Request-Method", method)
        if (headers != null) put("Access-Control-Request-Headers", headers)
    }

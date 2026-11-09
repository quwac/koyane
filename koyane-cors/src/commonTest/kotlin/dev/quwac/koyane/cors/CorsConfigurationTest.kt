package dev.quwac.koyane.cors

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CorsConfigurationTest {
    @Test
    fun rejectsUnsafeOrNonOriginConfigurationValues() {
        for (origin in listOf(
            "*", "null", "", "https://client.example/", "https://client.example/path",
            "https://client.example?x=1", "https://client.example#fragment", "https://user@client.example",
            "https://*.example", "https://client.example\r\nX-Test: yes", "https://client.example:65536",
            "https://client.example:999999999999", "https://日本.example", "https://CLIENT.example",
            "file://example", "client.example",
        )) {
            assertFailsWith<IllegalArgumentException>(origin) { corsApplication { allowOrigin(origin) } }
        }
        for (name in listOf("*", "", "X Test", "X-Test,Other", "X-Test:", "X-Test\r\nOther", "日本語")) {
            assertFailsWith<IllegalArgumentException>(name) { corsApplication { allowHeader(name) } }
            assertFailsWith<IllegalArgumentException>(name) { corsApplication { exposeHeader(name) } }
        }
    }

    @Test
    fun rejectsWildcardCredentialsInEitherConfigurationOrderAndNegativeMaxAge() {
        assertFailsWith<IllegalArgumentException> {
            corsApplication {
                allowAnyOrigin()
                allowCredentials = true
            }
        }
        assertFailsWith<IllegalArgumentException> {
            corsApplication {
                allowCredentials = true
                allowAnyOrigin()
            }
        }
        assertFailsWith<IllegalArgumentException> { corsApplication { maxAgeSeconds = -1 } }
    }

    @Test
    fun supportsSerializedLocalAndIpv6OriginsAndExplicitPorts() {
        for (origin in listOf(
            "http://localhost:8080",
            "https://[::1]:8443",
            "http://127.0.0.1:0",
            "https://xn--wgv71a.example",
        )) {
            val app = corsApplication { allowOrigin(origin) }
            assertEquals(origin, app.corsFetch(mapOf("Origin" to origin)).headers["Access-Control-Allow-Origin"])
            assertEquals(204, app.corsFetch(preflightHeaders(origin), "OPTIONS").status)
        }
    }

    @Test
    fun snapshotsConfigurationAndIsolatesPoliciesAcrossApplications() {
        lateinit var captured: CorsConfiguration
        val first = corsApplication {
            captured = this
            allowOrigin(CLIENT_ORIGIN)
            maxAgeSeconds = 20
        }
        captured.allowAnyOrigin()
        captured.allowOrigin("https://other.example")
        captured.allowMethod(HttpMethod.DELETE)
        captured.allowHeader("Authorization")
        captured.exposeHeader("X-Other")
        captured.maxAgeSeconds = 900
        captured.allowCredentials = true
        assertNull(first.corsFetch(mapOf("Origin" to "https://other.example")).headers["Access-Control-Allow-Origin"])
        assertNull(first.corsFetch(mapOf("Origin" to CLIENT_ORIGIN)).headers["Access-Control-Allow-Credentials"])
        assertNull(first.corsFetch(mapOf("Origin" to CLIENT_ORIGIN)).headers["Access-Control-Expose-Headers"])
        assertEquals("20", first.corsFetch(preflightHeaders(), "OPTIONS").headers["Access-Control-Max-Age"])
        assertEquals(403, first.corsFetch(preflightHeaders(method = "DELETE"), "OPTIONS").status)
        assertEquals(403, first.corsFetch(preflightHeaders(headers = "Authorization"), "OPTIONS").status)
        val second = corsApplication { allowOrigin("https://other.example") }
        assertNull(second.corsFetch(mapOf("Origin" to CLIENT_ORIGIN)).headers["Access-Control-Allow-Origin"])
        assertEquals(
            "https://other.example",
            second.corsFetch(mapOf("Origin" to "https://other.example"))
                .headers["Access-Control-Allow-Origin"],
        )
    }

    @Test
    fun rejectsDuplicateInstallation() {
        assertFailsWith<IllegalStateException> {
            koyane {
                install(Cors)
                install(Cors)
            }
        }
    }
}

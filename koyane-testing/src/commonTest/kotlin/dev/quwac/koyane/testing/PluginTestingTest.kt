package dev.quwac.koyane.testing

import dev.quwac.koyane.authentication.bearer.BearerAuthentication
import dev.quwac.koyane.authentication.bearer.authenticated
import dev.quwac.koyane.contentnegotiation.ContentCodec
import dev.quwac.koyane.contentnegotiation.ContentConverter
import dev.quwac.koyane.contentnegotiation.ContentNegotiation
import dev.quwac.koyane.contentnegotiation.receive
import dev.quwac.koyane.contentnegotiation.respond
import dev.quwac.koyane.core.Plugin
import dev.quwac.koyane.core.PluginScope
import dev.quwac.koyane.cors.Cors
import dev.quwac.koyane.errorhandling.ErrorHandling
import dev.quwac.koyane.http.ContentType
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpResponse
import dev.quwac.koyane.http.onResponse
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginTestingTest {
    @Test
    fun testsContentNegotiationWithExplicitCodecs() = testApplication {
        application {
            install(ContentNegotiation) { register(ContentType("application", "json"), JsonConverter) }
            install(Http) {
                post("/double") {
                    val value = receive { converter -> if (converter === JsonConverter) IntegerCodec else null }
                    respond(value * 2) { converter -> if (converter === JsonConverter) IntegerCodec else null }
                }
            }
        }

        val response = client.post(
            "/double",
            headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            body = "21",
        )
        assertEquals(200, response.status)
        assertEquals("42", response.bodyAsText())
        assertEquals("application/json", response.header("Content-Type"))

        val invalid = client.post(
            "/double",
            headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            body = "invalid",
        )
        assertEquals(400, invalid.status)
    }

    @Test
    fun testsErrorHandlingWithoutInterceptingTheFailureInTheClient() = testApplication {
        application {
            install(ErrorHandling) {
                exception<IllegalStateException> {
                    HttpResponse(503, "recovered", mapOf("X-Error" to "mapped"))
                }
            }
            install(Http) { get("/failure") { error("synthetic failure") } }
        }

        val response = client.get("/failure")

        assertEquals(503, response.status)
        assertEquals("recovered", response.bodyAsText())
        assertEquals("mapped", response.header("X-Error"))
    }

    @Test
    fun testsCorsRequestsAndPreflights() = testApplication {
        application {
            install(Cors) {
                allowOrigin("https://client.example")
                allowHeader("Authorization")
            }
            install(Http) { get("/") { text("ok") } }
        }

        val ordinary = client.get("/", headers = mapOf("Origin" to "https://client.example"))
        assertEquals(200, ordinary.status)
        assertEquals("https://client.example", ordinary.header("Access-Control-Allow-Origin"))

        val preflight = client.options(
            "/",
            headers = mapOf(
                "Origin" to "https://client.example",
                "Access-Control-Request-Method" to "GET",
                "Access-Control-Request-Headers" to "Authorization",
            ),
        )
        assertEquals(204, preflight.status)
        assertEquals("https://client.example", preflight.header("Access-Control-Allow-Origin"))
        assertEquals("authorization", preflight.header("Access-Control-Allow-Headers"))
    }

    @Test
    fun testsSuspendingBearerAuthenticationSuccessAndRejection() = testApplication {
        val users = BearerAuthentication<String>()
        application {
            install(users) {
                verify { token ->
                    delay(1)
                    token.takeIf { it == "accepted" }?.let { "Ada" }
                }
            }
            install(Http) {
                get("/private", authenticated(users) { user -> text("hello $user") })
            }
        }

        val accepted = client.get("/private", headers = mapOf("Authorization" to "Bearer accepted"))
        assertEquals(200, accepted.status)
        assertEquals("hello Ada", accepted.bodyAsText())

        assertEquals(401, client.get("/private").status)
        assertEquals(
            "Bearer realm=\"koyane\", error=\"invalid_token\"",
            client.get("/private", headers = mapOf("Authorization" to "Bearer rejected"))
                .header("WWW-Authenticate"),
        )
    }

    @Test
    fun preservesPluginInstallationOrderInTheProductionPipeline() = testApplication {
        val first = ResponseMarker("first")
        val second = ResponseMarker("second")
        application {
            install(first)
            install(second)
            install(Http) { get("/") { text("ok") } }
        }

        assertEquals("first,second", client.get("/").header("X-Plugin-Order"))
    }
}

private object JsonConverter : ContentConverter

private object IntegerCodec : ContentCodec<Int> {
    override fun decode(body: String): Int? = body.toIntOrNull()
    override fun encode(value: Int): String = value.toString()
}

private class ResponseMarker(private val value: String) : Plugin<Unit, Unit> {
    override fun createConfiguration() = Unit
    override fun install(configuration: Unit) = Unit

    override fun onInstall(scope: PluginScope, extension: Unit) {
        scope.onResponse { response ->
            val previous = response.headers["X-Plugin-Order"]
            val order = listOfNotNull(previous, value).joinToString(",")
            response.copy(headers = response.headers + ("X-Plugin-Order" to order))
        }
    }
}

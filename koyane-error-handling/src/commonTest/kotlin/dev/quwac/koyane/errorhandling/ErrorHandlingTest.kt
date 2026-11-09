package dev.quwac.koyane.errorhandling

import dev.quwac.koyane.contentnegotiation.ContentCodec
import dev.quwac.koyane.contentnegotiation.ContentConverter
import dev.quwac.koyane.contentnegotiation.ContentNegotiation
import dev.quwac.koyane.contentnegotiation.respond
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.ContentType
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpRequest
import dev.quwac.koyane.http.HttpResponse
import dev.quwac.koyane.http.HttpStatus
import dev.quwac.koyane.http.fetch
import dev.quwac.koyane.http.onResponse
import dev.quwac.koyane.http.text
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class ErrorHandlingTest {
    @Test
    fun installsAndMapsRegisteredExceptionsInFirstMatchOrder() {
        val specific = application {
            exception<MissingUser> { cause -> text("missing ${cause.id}", HttpStatus.NOT_FOUND) }
            exception<Throwable> { text("fallback", HttpStatus.INTERNAL_SERVER_ERROR) }
        }
        assertEquals(
            text("missing 7", HttpStatus.NOT_FOUND),
            immediate { specific.fetch(HttpRequest("GET", "/failure")) },
        )

        val broadFirst = application {
            exception<Throwable> { text("first", HttpStatus.INTERNAL_SERVER_ERROR) }
            exception<MissingUser> { text("unreachable", HttpStatus.NOT_FOUND) }
        }
        assertEquals("first", immediate { broadFirst.fetch(HttpRequest("GET", "/failure")) }.body)

        val duplicate = application {
            exception<MissingUser> { text("first", HttpStatus.NOT_FOUND) }
            exception<MissingUser> { text("second", HttpStatus.NOT_FOUND) }
        }
        assertEquals("first", immediate { duplicate.fetch(HttpRequest("GET", "/failure")) }.body)
    }

    @Test
    fun mapsClassifiedInputsAndPreservesOptionalSemantics() {
        val app = koyane {
            install(ContentNegotiation) { register(jsonType, JsonConverter) }
            install(ErrorHandling)
            install(Http) {
                get("/path/{id}") { text(param.long("id").toString()) }
                get("/missing/{id}") { text(param.string("other")) }
                get("/query") { text(query.int("value").toString()) }
                get("/header") { text(header.boolean("X-Enabled").toString()) }
                get("/optional") { text(query.longOrNull("value").toString()) }
                get("/parser") { text(query.parse("value") { error("parser bug") }) }
                post("/json") {
                    val value = application[ContentNegotiation].receive(request) { converter ->
                        if (converter === JsonConverter) IntegerCodec else null
                    }
                    text(value.toString())
                }
            }
        }
        for (request in listOf(
            HttpRequest("GET", "/path/no"),
            HttpRequest("GET", "/missing/1"),
            HttpRequest("GET", "/query"),
            HttpRequest("GET", "/query?value=no"),
            HttpRequest("GET", "/header"),
            HttpRequest("GET", "/header", mapOf("X-Enabled" to "yes")),
            HttpRequest("GET", "/optional?value=no"),
            HttpRequest("POST", "/json", mapOf("Content-Type" to "application/json"), "no"),
        )) {
            assertEquals(HttpStatus.BAD_REQUEST.code, immediate { app.fetch(request) }.status, request.url)
        }
        assertEquals("null", immediate { app.fetch(HttpRequest("GET", "/optional")) }.body)
        assertEquals("2", immediate { app.fetch(HttpRequest("GET", "/optional?value=2")) }.body)
        assertEquals(500, immediate { app.fetch(HttpRequest("GET", "/parser?value=1")) }.status)
    }

    @Test
    fun customizesRoutingFailuresAndPreservesAllowUnlessOverridden() {
        val app = koyane {
            install(ErrorHandling) {
                exception<Throwable> { text("application failure", HttpStatus.INTERNAL_SERVER_ERROR) }
                status(HttpStatus.NOT_FOUND) { text("custom 404", HttpStatus.NOT_FOUND) }
                status(HttpStatus.METHOD_NOT_ALLOWED) {
                    HttpResponse(HttpStatus.METHOD_NOT_ALLOWED.code, "custom 405", mapOf("X-Error" to "method"))
                }
            }
            install(Http) {
                post("/resource") { text("post") }
                get("/resource") { text("get") }
            }
        }
        assertEquals("custom 404", immediate { app.fetch(HttpRequest("GET", "/missing")) }.body)
        val method = immediate { app.fetch(HttpRequest("PUT", "/resource")) }
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED.code, method.status)
        assertEquals("custom 405", method.body)
        assertEquals("GET, POST", method.headers["Allow"])
        assertEquals("method", method.headers["X-Error"])

        val override = koyane {
            install(ErrorHandling) {
                status(HttpStatus.METHOD_NOT_ALLOWED) {
                    HttpResponse(HttpStatus.METHOD_NOT_ALLOWED.code, headers = mapOf("allow" to "OPTIONS"))
                }
            }
            install(Http) { get("/") { text("ok") } }
        }
        val overridden = immediate { override.fetch(HttpRequest("POST", "/")) }
        assertEquals("OPTIONS", overridden.headers["allow"])
        assertFalse("Allow" in overridden.headers)
    }

    @Test
    fun usesSafeUnhandledAndHandlerFailureResponsesWithoutLeakingDetails() {
        val secret = "internal-secret-value"
        val unhandled = application { }
        val response = immediate { unhandled.fetch(HttpRequest("GET", "/failure")) }
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.code, response.status)
        assertFalse(response.body.contains(secret))
        assertFalse(response.body.contains("MissingUser"))
        assertEquals("", response.body)

        val broken = application {
            exception<MissingUser> { error(secret) }
        }
        assertEquals(
            HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR.code),
            immediate { broken.fetch(HttpRequest("GET", "/failure")) },
        )

        val custom = application {
            status(HttpStatus.INTERNAL_SERVER_ERROR) {
                text("custom 500", HttpStatus.INTERNAL_SERVER_ERROR)
            }
        }
        assertEquals("custom 500", immediate { custom.fetch(HttpRequest("GET", "/failure")) }.body)

        val brokenStatus = application {
            status(HttpStatus.INTERNAL_SERVER_ERROR) { error(secret) }
        }
        assertEquals(
            HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR.code),
            immediate { brokenStatus.fetch(HttpRequest("GET", "/failure")) },
        )
    }

    @Test
    fun cancellationStillPropagates() {
        val cancellation = CancellationException("stop")
        val app = koyane {
            install(ErrorHandling)
            install(Http) { get("/") { throw cancellation } }
        }
        assertSame(
            cancellation,
            assertFailsWith<CancellationException> { immediate { app.fetch(HttpRequest("GET", "/")) } },
        )
    }

    @Test
    fun sendsTextEmptyAndExplicitCodecResponsesThroughTheNormalResponsePipeline() {
        val textApp = koyane {
            install(ErrorHandling) {
                status(HttpStatus.NOT_FOUND) { text("missing", HttpStatus.NOT_FOUND) }
                status(HttpStatus.METHOD_NOT_ALLOWED) { HttpResponse(HttpStatus.METHOD_NOT_ALLOWED.code) }
            }
            install(ResponseHeader)
            install(Http) { get("/") { text("ok") } }
        }
        val textResponse = immediate { textApp.fetch(HttpRequest("GET", "/missing")) }
        assertEquals("missing", textResponse.body)
        assertEquals("yes", textResponse.headers["X-Pipeline"])
        assertEquals("", immediate { textApp.fetch(HttpRequest("POST", "/")) }.body)

        val jsonApp = koyane {
            install(ContentNegotiation) { register(jsonType, JsonConverter) }
            install(ErrorHandling) {
                status(HttpStatus.NOT_FOUND) {
                    respond(ErrorBody("missing"), HttpStatus.NOT_FOUND) { converter ->
                        if (converter === JsonConverter) ErrorCodec else null
                    }
                }
            }
            install(Http) {}
        }
        val json = immediate {
            jsonApp.fetch(HttpRequest("GET", "/missing", mapOf("Accept" to "application/json")))
        }
        assertEquals(HttpStatus.NOT_FOUND.code, json.status)
        assertEquals("application/json; charset=utf-8", json.headers["Content-Type"])
        assertEquals("{\"error\":\"missing\"}", json.body)
        assertEquals(
            HttpStatus.NOT_ACCEPTABLE.code,
            immediate {
                jsonApp.fetch(HttpRequest("GET", "/missing", mapOf("Accept" to "text/plain")))
            }.status,
        )
    }

    @Test
    fun freezesConfigurationAndRejectsDuplicateInstallAndStatusRegistration() {
        lateinit var configuration: ErrorHandlingConfiguration
        val app = koyane {
            install(ErrorHandling) {
                configuration = this
                status(HttpStatus.NOT_FOUND) { text("installed", HttpStatus.NOT_FOUND) }
            }
            install(Http) {}
        }
        configuration.status(HttpStatus.INTERNAL_SERVER_ERROR) {
            text("late", HttpStatus.INTERNAL_SERVER_ERROR)
        }
        assertEquals("installed", immediate { app.fetch(HttpRequest("GET", "/")) }.body)
        assertFailsWith<IllegalArgumentException> {
            ErrorHandling.createConfiguration().apply {
                status(HttpStatus.NOT_FOUND) { HttpResponse(404) }
                status(HttpStatus.NOT_FOUND) { HttpResponse(404) }
            }
        }
        assertFailsWith<IllegalStateException> {
            koyane {
                install(ErrorHandling)
                install(ErrorHandling)
            }
        }
    }

    @Test
    fun preservesHttpBehaviorWhenThePluginIsNotInstalled() {
        val failure = MissingUser(7)
        val app = koyane {
            install(Http) {
                get("/input/{id}") { text(param.long("id").toString()) }
                get("/failure") { throw failure }
            }
        }
        assertEquals(400, immediate { app.fetch(HttpRequest("GET", "/input/no")) }.status)
        assertEquals(404, immediate { app.fetch(HttpRequest("GET", "/missing")) }.status)
        assertEquals(405, immediate { app.fetch(HttpRequest("POST", "/input/1")) }.status)
        assertSame(
            failure,
            assertFailsWith<MissingUser> { immediate { app.fetch(HttpRequest("GET", "/failure")) } },
        )
    }

    private fun application(configure: ErrorHandlingConfiguration.() -> Unit) = koyane {
        install(ErrorHandling, configure)
        install(Http) { get("/failure") { throw MissingUser(7) } }
    }
}

private class MissingUser(val id: Long) : RuntimeException("internal-secret-value")
private data class ErrorBody(val error: String)
private object JsonConverter : ContentConverter
private object IntegerCodec : ContentCodec<Int> {
    override fun decode(body: String): Int? = body.toIntOrNull()
    override fun encode(value: Int): String = value.toString()
}
private object ErrorCodec : ContentCodec<ErrorBody> {
    override fun decode(body: String): ErrorBody? = null
    override fun encode(value: ErrorBody): String = "{\"error\":\"${value.error}\"}"
}
private val jsonType = ContentType("application", "json", mapOf("charset" to "utf-8"))

private object ResponseHeader : dev.quwac.koyane.core.Plugin<Unit, Unit> {
    override fun createConfiguration() = Unit
    override fun install(configuration: Unit) = Unit
    override fun onInstall(scope: dev.quwac.koyane.core.PluginScope, extension: Unit) {
        scope.onResponse { response -> response.copy(headers = response.headers + ("X-Pipeline" to "yes")) }
    }
}

private fun <T> immediate(block: suspend () -> T): T {
    var completed: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            completed = result
        }
    })
    return checkNotNull(completed) { "This test expects immediate completion" }.getOrThrow()
}

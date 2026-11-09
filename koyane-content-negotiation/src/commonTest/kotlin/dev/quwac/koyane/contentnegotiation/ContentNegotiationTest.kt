package dev.quwac.koyane.contentnegotiation

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.ContentType
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpRequest
import dev.quwac.koyane.http.HttpStatus
import dev.quwac.koyane.http.fetch
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ContentNegotiationTest {
    @Test
    fun selectsRequestConverterUsingContentTypeWithoutParameters() {
        val app = koyane {
            install(ContentNegotiation) {
                register(jsonType, JsonConverter)
                register(textType, TextConverter)
            }
            install(Http) {
                post("/") {
                    val value = receive { converter ->
                        when (converter) {
                            JsonConverter -> IntegerCodec()
                            TextConverter -> IntegerCodec(decodeOffset = 100)
                            else -> null
                        }
                    }
                    text(value.toString())
                }
            }
        }
        val json = immediate {
            app.fetch(HttpRequest("POST", "/", mapOf("content-type" to "application/json; charset=utf-8"), "7"))
        }
        val text = immediate {
            app.fetch(HttpRequest("POST", "/", mapOf("Content-Type" to "text/plain; charset=us-ascii"), "7"))
        }
        assertEquals("7", json.body)
        assertEquals("107", text.body)
    }

    @Test
    fun selectsResponsesUsingExactWildcardAndQualityPreferences() {
        val app = responseApplication()
        val cases = listOf(
            "application/json" to "json:7",
            "application/json; charset=utf-8" to "json:7",
            "application/*" to "json:7",
            "*/*" to "json:7",
            "text/plain;q=0.4, application/json;q=0.8" to "json:7",
            "application/json;q=0.4, text/plain;q=0.8" to "text:7",
            "application/*;q=0.8, application/json;q=0, text/plain;q=0.5" to "text:7",
        )
        for ((accept, expected) in cases) {
            val response = immediate { app.fetch(HttpRequest("GET", "/", mapOf("Accept" to accept))) }
            assertEquals(expected, response.body, accept)
        }
    }

    @Test
    fun returnsTheSelectedResponseContentTypeAndUsesRegistrationOrderWithoutAccept() {
        val response = immediate { responseApplication().fetch(HttpRequest("GET", "/")) }
        assertEquals("json:7", response.body)
        assertEquals("application/json; charset=utf-8", response.headers["Content-Type"])
    }

    @Test
    fun mapsUnsupportedMissingAndMalformedRequestContentTypesTo415() {
        val app = requestApplication()
        for (headers in listOf(
            emptyMap(),
            mapOf("Content-Type" to "image/png"),
            mapOf("Content-Type" to "application"),
        )) {
            assertEquals(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.code,
                immediate { app.fetch(HttpRequest("POST", "/", headers, "7")) }.status,
            )
        }
    }

    @Test
    fun mapsMalformedBodiesTo400AndUnacceptableResponsesTo406() {
        assertEquals(
            HttpStatus.BAD_REQUEST.code,
            immediate {
                requestApplication().fetch(
                    HttpRequest("POST", "/", mapOf("Content-Type" to "application/json"), "invalid"),
                )
            }.status,
        )
        for (accept in listOf("image/png", "application/json;q=0", "broken")) {
            assertEquals(
                HttpStatus.NOT_ACCEPTABLE.code,
                immediate { responseApplication().fetch(HttpRequest("GET", "/", mapOf("Accept" to accept))) }.status,
                accept,
            )
        }
    }

    @Test
    fun doesNotConvertWithoutThePluginOrWithoutRegistrations() {
        val missingPlugin = koyane {
            install(Http) {
                get("/") { respond(7) { IntegerCodec() } }
            }
        }
        assertFailsWith<IllegalStateException> {
            immediate { missingPlugin.fetch(HttpRequest("GET", "/")) }
        }

        val emptyPlugin = koyane {
            install(ContentNegotiation)
            install(Http) {
                post("/") { text(receive { IntegerCodec() }.toString()) }
                get("/") { respond(7) { IntegerCodec() } }
            }
        }
        assertEquals(
            415,
            immediate {
                emptyPlugin.fetch(HttpRequest("POST", "/", mapOf("Content-Type" to "application/json"), "7"))
            }.status,
        )
        assertEquals(406, immediate { emptyPlugin.fetch(HttpRequest("GET", "/")) }.status)
    }
}

private object JsonConverter : ContentConverter
private object TextConverter : ContentConverter
private val jsonType = ContentType("application", "json", mapOf("charset" to "utf-8"))
private val textType = ContentType("text", "plain", mapOf("charset" to "utf-8"))

private class IntegerCodec(private val label: String = "", private val decodeOffset: Int = 0) : ContentCodec<Int> {
    override fun decode(body: String): Int? = body.toIntOrNull()?.plus(decodeOffset)
    override fun encode(value: Int): String = "$label$value"
}

private fun requestApplication() = koyane {
    install(ContentNegotiation) { register(jsonType, JsonConverter) }
    install(Http) {
        post(
            "/",
        ) { text(receive { converter -> if (converter === JsonConverter) IntegerCodec() else null }.toString()) }
    }
}

private fun responseApplication() = koyane {
    install(ContentNegotiation) {
        register(jsonType, JsonConverter)
        register(textType, TextConverter)
    }
    install(Http) {
        get("/") {
            respond(7) { converter ->
                when (converter) {
                    JsonConverter -> IntegerCodec("json:")
                    TextConverter -> IntegerCodec("text:")
                    else -> null
                }
            }
        }
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

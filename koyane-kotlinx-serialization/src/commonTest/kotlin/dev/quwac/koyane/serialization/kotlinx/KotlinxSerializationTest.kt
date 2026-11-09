package dev.quwac.koyane.serialization.kotlinx

import dev.quwac.koyane.contentnegotiation.ContentNegotiation
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpRequest
import dev.quwac.koyane.http.HttpStatus
import dev.quwac.koyane.http.fetch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KotlinxSerializationTest {
    @Test
    fun codecUsesExplicitSerializersAndTheSuppliedJsonFormat() {
        val default = KotlinxJsonCodec(NoteSerializer)
        val configured = KotlinxJsonCodec(
            NoteSerializer,
            Json {
                ignoreUnknownKeys = true
                allowTrailingComma = true
                prettyPrint = true
            },
        )
        val note = Note("hello", 2)
        assertEquals(note, default.decode("{\"count\":2,\"text\":\"hello\"}"))
        assertEquals("{\"text\":\"hello\",\"count\":2}", default.encode(note))
        assertNull(default.decode("{\"text\":\"hello\",\"count\":2,\"extra\":true}"))
        assertEquals(note, configured.decode("{\"text\":\"hello\",\"count\":2,\"extra\":true,}"))
        assertTrue(configured.encode(note).contains('\n'))
    }

    @Test
    fun receivesAndRespondsWithExplicitSerializers() {
        val app = koyane {
            install(ContentNegotiation) { json() }
            install(Http) {
                post("/") {
                    respond(receive(NoteSerializer), NoteSerializer, status = HttpStatus.CREATED)
                }
            }
        }
        val response = immediate {
            app.fetch(
                HttpRequest(
                    "POST",
                    "/",
                    mapOf("Content-Type" to "application/json; charset=utf-8", "Accept" to "application/json"),
                    "{\"text\":\"hello\",\"count\":2}",
                ),
            )
        }
        assertEquals(201, response.status)
        assertEquals("{\"text\":\"hello\",\"count\":2}", response.body)
        assertEquals("application/json; charset=utf-8", response.headers["Content-Type"])
    }

    @Test
    fun mapsMalformedJsonAndSerializerDecodeFailuresTo400() {
        val app = koyane {
            install(ContentNegotiation) { json() }
            install(Http) {
                post("/") { respond(receive(NoteSerializer), NoteSerializer) }
            }
        }
        for (body in listOf("", "{broken}", "{}", "{\"text\":\"hello\",\"count\":[]}")) {
            assertEquals(
                400,
                immediate {
                    app.fetch(HttpRequest("POST", "/", mapOf("Content-Type" to "application/json"), body))
                }.status,
                body,
            )
        }
    }

    @Test
    fun usesConfiguredJsonDuringNegotiatedRequestsAndResponses() {
        val app = koyane {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(Http) {
                post("/") { respond(receive(NoteSerializer), NoteSerializer) }
            }
        }
        val response = immediate {
            app.fetch(
                HttpRequest(
                    "POST",
                    "/",
                    mapOf("Content-Type" to "application/json", "Accept" to "*/*"),
                    "{\"text\":\"hello\",\"count\":2,\"extra\":true}",
                ),
            )
        }
        assertEquals(200, response.status)
        assertEquals("{\"text\":\"hello\",\"count\":2}", response.body)
    }

    @Test
    fun requiresBothContentNegotiationAndAJsonRegistration() {
        val missingPlugin = koyane {
            install(Http) { get("/") { respond(1, Int.serializer()) } }
        }
        assertFailsWith<IllegalStateException> {
            immediate { missingPlugin.fetch(HttpRequest("GET", "/")) }
        }

        val noJson = koyane {
            install(ContentNegotiation)
            install(Http) {
                post("/") { respond(receive(Int.serializer()), Int.serializer()) }
                get("/") { respond(1, Int.serializer()) }
            }
        }
        assertEquals(
            415,
            immediate {
                noJson.fetch(HttpRequest("POST", "/", mapOf("Content-Type" to "application/json"), "1"))
            }.status,
        )
        assertEquals(406, immediate { noJson.fetch(HttpRequest("GET", "/")) }.status)
    }

    @Test
    fun decodingExceptionsRepresentInvalidInputButCancellationPropagates() {
        for (failure in listOf(
            SerializationException("invalid"),
            IllegalArgumentException("bug"),
            IllegalStateException("bug"),
        )) {
            assertNull(KotlinxJsonCodec(failingSerializer(failure)).decode("1"))
        }
        val cancellation = CancellationException("stop")
        val thrown = assertFailsWith<CancellationException> {
            KotlinxJsonCodec(failingSerializer(cancellation)).decode("1")
        }
        assertSame(cancellation, thrown)
    }

    @Test
    fun encodingFailuresRemainApplicationFailures() {
        val failure = SerializationException("encoding failed")
        val app = koyane {
            install(ContentNegotiation) { json() }
            install(Http) { get("/") { respond(1, failingSerializer(failure)) } }
        }
        assertSame(
            failure,
            assertFailsWith<SerializationException> {
                immediate { app.fetch(HttpRequest("GET", "/")) }
            },
        )
    }
}

private data class Note(val text: String, val count: Int)

private object NoteSerializer : KSerializer<Note> {
    override val descriptor = buildClassSerialDescriptor("Note") {
        element("text", String.serializer().descriptor)
        element("count", Int.serializer().descriptor)
    }

    override fun serialize(encoder: Encoder, value: Note) = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.text)
        encodeIntElement(descriptor, 1, value.count)
    }

    override fun deserialize(decoder: Decoder): Note = decoder.decodeStructure(descriptor) {
        var text: String? = null
        var count: Int? = null
        while (true) {
            when (decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break
                0 -> text = decodeStringElement(descriptor, 0)
                1 -> count = decodeIntElement(descriptor, 1)
                else -> throw SerializationException("Unexpected field")
            }
        }
        Note(
            text ?: throw SerializationException("Missing text"),
            count ?: throw SerializationException("Missing count"),
        )
    }
}

private fun failingSerializer(failure: Exception): KSerializer<Int> = object : KSerializer<Int> by Int.serializer() {
    override fun deserialize(decoder: Decoder): Int = throw failure
    override fun serialize(encoder: Encoder, value: Int): Unit = throw failure
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

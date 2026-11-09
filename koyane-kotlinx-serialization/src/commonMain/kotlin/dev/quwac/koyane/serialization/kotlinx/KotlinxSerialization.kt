package dev.quwac.koyane.serialization.kotlinx

import dev.quwac.koyane.contentnegotiation.ContentCodec
import dev.quwac.koyane.contentnegotiation.ContentConverter
import dev.quwac.koyane.contentnegotiation.ContentNegotiationConfiguration
import dev.quwac.koyane.http.ContentType
import dev.quwac.koyane.http.HandlerScope
import dev.quwac.koyane.http.HttpCall
import dev.quwac.koyane.http.HttpResponse
import dev.quwac.koyane.http.HttpStatus
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import dev.quwac.koyane.contentnegotiation.receive as receiveContent
import dev.quwac.koyane.contentnegotiation.respond as respondContent

private val jsonContentType = ContentType("application", "json", mapOf("charset" to "utf-8"))

private class KotlinxJsonConverter(val format: Json) : ContentConverter

/** Adapts an explicit serializer and JSON format without runtime type discovery. */
class KotlinxJsonCodec<T : Any>(private val serializer: KSerializer<T>, private val format: Json = Json) :
    ContentCodec<T> {
    /** JSON decoding failures represent invalid input; cancellation still propagates. */
    override fun decode(body: String): T? = try {
        format.decodeFromString(serializer, body)
    } catch (failure: SerializationException) {
        (failure.cause as? CancellationException)?.let { throw it }
        null
    }

    override fun encode(value: T): String = format.encodeToString(serializer, value)
}

/** Registers application/json with an explicitly supplied or default Json format. */
fun ContentNegotiationConfiguration.json(format: Json = Json) {
    register(jsonContentType, KotlinxJsonConverter(format))
}

fun <T : Any> HandlerScope.receive(serializer: KSerializer<T>): T = receiveContent { converter ->
    (converter as? KotlinxJsonConverter)?.let { KotlinxJsonCodec(serializer, it.format) }
}

fun <T : Any> HandlerScope.respond(
    value: T,
    serializer: KSerializer<T>,
    status: HttpStatus = HttpStatus.OK,
): HttpResponse = respondContent(value, status) { converter ->
    (converter as? KotlinxJsonConverter)?.let { KotlinxJsonCodec(serializer, it.format) }
}

/** Encodes a hook response as JSON with an explicit serializer. */
fun <T : Any> HttpCall.respond(
    value: T,
    serializer: KSerializer<T>,
    status: HttpStatus = HttpStatus.OK,
): HttpResponse = respondContent(value, status) { converter ->
    (converter as? KotlinxJsonConverter)?.let { KotlinxJsonCodec(serializer, it.format) }
}

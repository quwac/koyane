package dev.quwac.koyane.contentnegotiation

import dev.quwac.koyane.core.Plugin
import dev.quwac.koyane.http.ContentType
import dev.quwac.koyane.http.HandlerScope
import dev.quwac.koyane.http.HttpCall
import dev.quwac.koyane.http.HttpFailure
import dev.quwac.koyane.http.HttpRequest
import dev.quwac.koyane.http.HttpResponse
import dev.quwac.koyane.http.HttpStatus
import dev.quwac.koyane.http.InvalidHttpInput

/** Identifies one content format implementation registered with Content Negotiation. */
interface ContentConverter

/** Converts a value with explicit type information supplied by an adapter or application. */
interface ContentCodec<T : Any> {
    /** Returns null when the body cannot be decoded as a valid value. */
    fun decode(body: String): T?
    fun encode(value: T): String
}

class ContentNegotiationConfiguration {
    private val registrations = mutableListOf<ContentRegistration>()

    /** Registers one response media type and the converter that owns it. */
    fun register(contentType: ContentType, converter: ContentConverter) {
        require(registrations.none { it.contentType.matches(contentType) }) {
            "A converter is already registered for ${contentType.type}/${contentType.subtype}"
        }
        registrations += ContentRegistration(contentType, converter)
    }

    internal fun build(): ContentNegotiationApplication = ContentNegotiationApplication(registrations.toList())
}

object ContentNegotiation : Plugin<ContentNegotiationConfiguration, ContentNegotiationApplication> {
    override fun createConfiguration(): ContentNegotiationConfiguration = ContentNegotiationConfiguration()
    override fun install(configuration: ContentNegotiationConfiguration): ContentNegotiationApplication =
        configuration.build()
}

class ContentNegotiationApplication internal constructor(private val registrations: List<ContentRegistration>) {
    /** Selects from Content-Type, then decodes with the codec supplied for that converter. */
    fun <T : Any> receive(request: HttpRequest, codec: (ContentConverter) -> ContentCodec<T>?): T {
        val contentType = request.header("Content-Type")?.let(ContentType::parse) ?: unsupportedContentType()
        val selected = registrations.firstNotNullOfOrNull { registration ->
            if (registration.contentType.matches(contentType)) codec(registration.converter) else null
        } ?: unsupportedContentType()
        return selected.decode(request.body) ?: invalidContent()
    }

    /** Selects from Accept, then encodes an ordinary HttpResponse with the selected Content-Type. */
    fun <T : Any> respond(
        request: HttpRequest,
        value: T,
        status: HttpStatus = HttpStatus.OK,
        codec: (ContentConverter) -> ContentCodec<T>?,
    ): HttpResponse {
        val supported = registrations.mapIndexedNotNull { index, registration ->
            codec(registration.converter)?.let { SupportedContent(index, registration, it) }
        }
        val selected = selectResponse(request.header("Accept"), supported) ?: unacceptableContent()
        return HttpResponse(
            status = status.code,
            body = selected.codec.encode(value),
            headers = mapOf("Content-Type" to selected.registration.contentType.toString()),
        )
    }
}

fun <T : Any> HandlerScope.receive(codec: (ContentConverter) -> ContentCodec<T>?): T =
    application[ContentNegotiation].receive(request, codec)

fun <T : Any> HandlerScope.respond(
    value: T,
    status: HttpStatus = HttpStatus.OK,
    codec: (ContentConverter) -> ContentCodec<T>?,
): HttpResponse = call.respond(value, status, codec)

/** Encodes a hook response using the call's Accept header and an explicit codec. */
fun <T : Any> HttpCall.respond(
    value: T,
    status: HttpStatus = HttpStatus.OK,
    codec: (ContentConverter) -> ContentCodec<T>?,
): HttpResponse = application[ContentNegotiation].respond(request, value, status, codec)

internal data class ContentRegistration(val contentType: ContentType, val converter: ContentConverter)

private fun HttpRequest.header(name: String): String? = headers.entries.firstOrNull {
    it.key.equals(name, ignoreCase = true)
}?.value

private fun unsupportedContentType(): Nothing = throw HttpFailure(HttpResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE.code))

private fun invalidContent(): Nothing = throw InvalidHttpInput()

private fun unacceptableContent(): Nothing = throw HttpFailure(HttpResponse(HttpStatus.NOT_ACCEPTABLE.code))

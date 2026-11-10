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

    /** Encodes [value] as a text body; encoding failures propagate to the caller. */
    fun encode(value: T): String
}

/** Registers converters in response-preference order; each concrete media type may be registered once. */
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

/** Installs explicit request decoding and Accept-based response encoding without depending on a serialization library. */
object ContentNegotiation : Plugin<ContentNegotiationConfiguration, ContentNegotiationApplication> {
    override fun createConfiguration(): ContentNegotiationConfiguration = ContentNegotiationConfiguration()
    override fun install(configuration: ContentNegotiationConfiguration): ContentNegotiationApplication =
        configuration.build()
}

/** The immutable converter registrations belonging to one application's [ContentNegotiation] plugin. */
class ContentNegotiationApplication internal constructor(private val registrations: List<ContentRegistration>) {
    /**
     * Selects a converter by request Content-Type, ignoring media type parameters, then decodes the buffered body.
     * [codec] returns null for converters that cannot handle [T]. Missing, malformed, or unsupported Content-Type
     * produces HTTP 415; a null decode result throws [InvalidHttpInput] for HTTP 400. Codec exceptions propagate.
     */
    fun <T : Any> receive(request: HttpRequest, codec: (ContentConverter) -> ContentCodec<T>?): T {
        val contentType = request.header("Content-Type")?.let(ContentType::parse) ?: unsupportedContentType()
        val selected = registrations.firstNotNullOfOrNull { registration ->
            if (registration.contentType.matches(contentType)) codec(registration.converter) else null
        } ?: unsupportedContentType()
        return selected.decode(request.body) ?: invalidContent()
    }

    /**
     * Negotiates Accept against converters supported by [codec] and encodes [value] with the selected Content-Type.
     * An absent Accept header selects the first supported registration. Quality, specificity, header order,
     * then registration order decide between candidates; no acceptable format produces HTTP 406.
     * Encoding failures propagate and [HttpResponse] validates the body against [status].
     */
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

/** Decodes this request through the installed [ContentNegotiation] plugin; see [ContentNegotiationApplication.receive]. */
fun <T : Any> HandlerScope.receive(codec: (ContentConverter) -> ContentCodec<T>?): T =
    application[ContentNegotiation].receive(request, codec)

/** Negotiates and encodes a response through installed [ContentNegotiation]; see [ContentNegotiationApplication.respond]. */
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

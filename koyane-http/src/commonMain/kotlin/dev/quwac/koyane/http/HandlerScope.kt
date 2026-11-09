package dev.quwac.koyane.http

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.core.Attributes
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Missing or invalid external HTTP input classified as a 400 response. */
class InvalidHttpInput : HttpFailure(empty(HttpStatus.BAD_REQUEST))

/** Missing required values and invalid present values are HTTP 400 errors. */
@Suppress("TooManyFunctions") // Each supported type has required and optional extraction.
class InputValues internal constructor(private val lookup: (String) -> String?) {
    fun string(name: String): String = required(lookup(name)) { it }
    fun stringOrNull(name: String): String? = lookup(name)
    fun long(name: String, default: Long? = null): Long = required(lookup(name), default, String::toLongOrNull)
    fun longOrNull(name: String): Long? = optional(lookup(name), String::toLongOrNull)
    fun int(name: String, default: Int? = null): Int = required(lookup(name), default, String::toIntOrNull)
    fun intOrNull(name: String): Int? = optional(lookup(name), String::toIntOrNull)
    fun byte(name: String, default: Byte? = null): Byte = required(lookup(name), default, String::toByteOrNull)
    fun byteOrNull(name: String): Byte? = optional(lookup(name), String::toByteOrNull)
    fun short(name: String, default: Short? = null): Short = required(lookup(name), default, String::toShortOrNull)
    fun shortOrNull(name: String): Short? = optional(lookup(name), String::toShortOrNull)
    fun float(name: String, default: Float? = null): Float = required(lookup(name), default, String::toFloatOrNull)
    fun floatOrNull(name: String): Float? = optional(lookup(name), String::toFloatOrNull)
    fun double(name: String, default: Double? = null): Double = required(lookup(name), default, String::toDoubleOrNull)
    fun doubleOrNull(name: String): Double? = optional(lookup(name), String::toDoubleOrNull)
    fun ubyte(name: String, default: UByte? = null): UByte = required(lookup(name), default, String::toUByteOrNull)
    fun ubyteOrNull(name: String): UByte? = optional(lookup(name), String::toUByteOrNull)
    fun ushort(name: String, default: UShort? = null): UShort = required(lookup(name), default, String::toUShortOrNull)
    fun ushortOrNull(name: String): UShort? = optional(lookup(name), String::toUShortOrNull)
    fun uint(name: String, default: UInt? = null): UInt = required(lookup(name), default, String::toUIntOrNull)
    fun uintOrNull(name: String): UInt? = optional(lookup(name), String::toUIntOrNull)
    fun ulong(name: String, default: ULong? = null): ULong = required(lookup(name), default, String::toULongOrNull)
    fun ulongOrNull(name: String): ULong? = optional(lookup(name), String::toULongOrNull)
    fun instant(name: String, default: Instant? = null): Instant = required(lookup(name), default, Instant::parseOrNull)
    fun instantOrNull(name: String): Instant? = optional(lookup(name), Instant::parseOrNull)
    fun duration(name: String, default: Duration? = null): Duration =
        required(lookup(name), default, Duration::parseOrNull)
    fun durationOrNull(name: String): Duration? = optional(lookup(name), Duration::parseOrNull)
    fun boolean(name: String, default: Boolean? = null): Boolean = required(
        lookup(name),
        default,
        String::toBooleanStrictOrNull,
    )
    fun booleanOrNull(name: String): Boolean? = optional(lookup(name), String::toBooleanStrictOrNull)
    fun uuid(name: String): Uuid = required(lookup(name), parse = Uuid::parseHexDashOrNull)
    fun uuidOrNull(name: String): Uuid? = optional(lookup(name), Uuid::parseHexDashOrNull)

    /** A null parser result is invalid input; unexpected parser exceptions propagate. */
    fun <T : Any> parse(name: String, parser: (String) -> T?): T = required(lookup(name), parse = parser)

    /** The default applies only to missing values; present values must parse successfully. */
    fun <T : Any> parse(name: String, default: T, parser: (String) -> T?): T = required(lookup(name), default, parser)

    /** Returns null only for missing values; a null parser result is invalid input. */
    fun <T : Any> parseOrNull(name: String, parser: (String) -> T?): T? = optional(lookup(name), parser)
}

private fun <T : Any> required(value: String?, default: T? = null, parse: (String) -> T?): T {
    if (value == null) return default ?: throw InvalidHttpInput()
    return parse(value) ?: throw InvalidHttpInput()
}

private fun <T : Any> optional(value: String?, parse: (String) -> T?): T? {
    if (value == null) return null
    return parse(value) ?: throw InvalidHttpInput()
}

class RequestBody internal constructor(private val value: String) {
    fun text(): String = value
}

class HandlerScope internal constructor(
    val call: HttpCall,
    parameters: Map<String, String>,
    queries: Map<String, String>,
) {
    val application: Application get() = call.application
    val attributes: Attributes get() = call.attributes
    val request: HttpRequest get() = call.request
    val param = InputValues(parameters::get)
    val query = InputValues(queries::get)
    val header = InputValues { name ->
        request.headers.entries.firstOrNull {
            it.key.equals(
                name,
                ignoreCase = true,
            )
        }?.value
    }
    val body = RequestBody(request.body)

    fun text(body: String, status: HttpStatus = HttpStatus.OK): HttpResponse = dev.quwac.koyane.http.text(
        body,
        status,
    )
    fun empty(status: HttpStatus): HttpResponse = dev.quwac.koyane.http.empty(status)
    fun redirect(location: String, status: HttpStatus = HttpStatus.FOUND): HttpResponse =
        dev.quwac.koyane.http.redirect(location, status)
}

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
    /** Returns the unparsed value of [name]; throws [InvalidHttpInput] if it is missing. */
    fun string(name: String): String = required(lookup(name)) { it }

    /** Returns the unparsed value of [name], or null if it is missing; empty strings are preserved. */
    fun stringOrNull(name: String): String? = lookup(name)

    /**
     * Reads [name] as a [Long].
     * Uses a non-null [default] only when absent; missing or invalid input otherwise throws [InvalidHttpInput].
     */
    fun long(name: String, default: Long? = null): Long = required(lookup(name), default, String::toLongOrNull)

    /**
     * Reads [name] as a [Long].
     * Returns null only when absent; invalid present input throws [InvalidHttpInput].
     */
    fun longOrNull(name: String): Long? = optional(lookup(name), String::toLongOrNull)

    /**
     * Reads [name] as an [Int].
     * Uses a non-null [default] only when absent; missing or invalid input otherwise throws [InvalidHttpInput].
     */
    fun int(name: String, default: Int? = null): Int = required(lookup(name), default, String::toIntOrNull)

    /**
     * Reads [name] as an [Int].
     * Returns null only when absent; invalid present input throws [InvalidHttpInput].
     */
    fun intOrNull(name: String): Int? = optional(lookup(name), String::toIntOrNull)

    /**
     * Reads [name] as a [Byte].
     * Uses a non-null [default] only when absent; missing or invalid input otherwise throws [InvalidHttpInput].
     */
    fun byte(name: String, default: Byte? = null): Byte = required(lookup(name), default, String::toByteOrNull)

    /**
     * Reads [name] as a [Byte].
     * Returns null only when absent; invalid present input throws [InvalidHttpInput].
     */
    fun byteOrNull(name: String): Byte? = optional(lookup(name), String::toByteOrNull)

    /**
     * Reads [name] as a [Short].
     * Uses a non-null [default] only when absent; missing or invalid input otherwise throws [InvalidHttpInput].
     */
    fun short(name: String, default: Short? = null): Short = required(lookup(name), default, String::toShortOrNull)

    /**
     * Reads [name] as a [Short].
     * Returns null only when absent; invalid present input throws [InvalidHttpInput].
     */
    fun shortOrNull(name: String): Short? = optional(lookup(name), String::toShortOrNull)

    /**
     * Reads [name] as a [Float].
     * Uses a non-null [default] only when absent; missing or invalid input otherwise throws [InvalidHttpInput].
     */
    fun float(name: String, default: Float? = null): Float = required(lookup(name), default, String::toFloatOrNull)

    /**
     * Reads [name] as a [Float].
     * Returns null only when absent; invalid present input throws [InvalidHttpInput].
     */
    fun floatOrNull(name: String): Float? = optional(lookup(name), String::toFloatOrNull)

    /**
     * Reads [name] as a [Double].
     * Uses a non-null [default] only when absent; missing or invalid input otherwise throws [InvalidHttpInput].
     */
    fun double(name: String, default: Double? = null): Double = required(lookup(name), default, String::toDoubleOrNull)

    /**
     * Reads [name] as a [Double].
     * Returns null only when absent; invalid present input throws [InvalidHttpInput].
     */
    fun doubleOrNull(name: String): Double? = optional(lookup(name), String::toDoubleOrNull)

    /**
     * Reads [name] as a [UByte].
     * Uses a non-null [default] only when absent; missing or invalid input otherwise throws [InvalidHttpInput].
     */
    fun ubyte(name: String, default: UByte? = null): UByte = required(lookup(name), default, String::toUByteOrNull)

    /**
     * Reads [name] as a [UByte].
     * Returns null only when absent; invalid present input throws [InvalidHttpInput].
     */
    fun ubyteOrNull(name: String): UByte? = optional(lookup(name), String::toUByteOrNull)

    /**
     * Reads [name] as a [UShort].
     * Uses a non-null [default] only when absent; missing or invalid input otherwise throws [InvalidHttpInput].
     */
    fun ushort(name: String, default: UShort? = null): UShort = required(lookup(name), default, String::toUShortOrNull)

    /**
     * Reads [name] as a [UShort].
     * Returns null only when absent; invalid present input throws [InvalidHttpInput].
     */
    fun ushortOrNull(name: String): UShort? = optional(lookup(name), String::toUShortOrNull)

    /**
     * Reads [name] as a [UInt].
     * Uses a non-null [default] only when absent; missing or invalid input otherwise throws [InvalidHttpInput].
     */
    fun uint(name: String, default: UInt? = null): UInt = required(lookup(name), default, String::toUIntOrNull)

    /**
     * Reads [name] as a [UInt].
     * Returns null only when absent; invalid present input throws [InvalidHttpInput].
     */
    fun uintOrNull(name: String): UInt? = optional(lookup(name), String::toUIntOrNull)

    /**
     * Reads [name] as a [ULong].
     * Uses a non-null [default] only when absent; missing or invalid input otherwise throws [InvalidHttpInput].
     */
    fun ulong(name: String, default: ULong? = null): ULong = required(lookup(name), default, String::toULongOrNull)

    /**
     * Reads [name] as a [ULong].
     * Returns null only when absent; invalid present input throws [InvalidHttpInput].
     */
    fun ulongOrNull(name: String): ULong? = optional(lookup(name), String::toULongOrNull)

    /**
     * Reads [name] as an ISO-8601 [Instant].
     * Uses a non-null [default] only when absent; missing or invalid input otherwise throws [InvalidHttpInput].
     */
    fun instant(name: String, default: Instant? = null): Instant = required(lookup(name), default, Instant::parseOrNull)

    /**
     * Reads [name] as an ISO-8601 [Instant].
     * Returns null only when absent; invalid present input throws [InvalidHttpInput].
     */
    fun instantOrNull(name: String): Instant? = optional(lookup(name), Instant::parseOrNull)

    /**
     * Reads [name] as a [Duration] using [Duration.parseOrNull].
     * Uses a non-null [default] only when absent; missing or invalid input otherwise throws [InvalidHttpInput].
     */
    fun duration(name: String, default: Duration? = null): Duration =
        required(lookup(name), default, Duration::parseOrNull)

    /**
     * Reads [name] as a [Duration] using [Duration.parseOrNull].
     * Returns null only when absent; invalid present input throws [InvalidHttpInput].
     */
    fun durationOrNull(name: String): Duration? = optional(lookup(name), Duration::parseOrNull)

    /**
     * Reads [name] as a [Boolean] accepting only lowercase `true` or `false`.
     * Uses a non-null [default] only when absent; missing or invalid input otherwise throws [InvalidHttpInput].
     */
    fun boolean(name: String, default: Boolean? = null): Boolean = required(
        lookup(name),
        default,
        String::toBooleanStrictOrNull,
    )

    /**
     * Reads [name] as a [Boolean] accepting only lowercase `true` or `false`.
     * Returns null only when absent; invalid present input throws [InvalidHttpInput].
     */
    fun booleanOrNull(name: String): Boolean? = optional(lookup(name), String::toBooleanStrictOrNull)

    /** Reads a hex-and-dash UUID from [name]; missing or invalid input throws [InvalidHttpInput]. */
    fun uuid(name: String): Uuid = required(lookup(name), parse = Uuid::parseHexDashOrNull)

    /** Reads a hex-and-dash UUID from [name]; absence returns null and invalid input throws [InvalidHttpInput]. */
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

/** The request body buffered as text by the runtime adapter. */
class RequestBody internal constructor(private val value: String) {
    /** Returns the request body unchanged, including an empty string when no text was supplied. */
    fun text(): String = value
}

/**
 * Context for a matched HTTP route, with explicit typed extraction and response builders.
 *
 * @property call The HTTP call shared with lifecycle hooks.
 */
class HandlerScope internal constructor(
    val call: HttpCall,
    parameters: Map<String, String>,
    queries: Map<String, String>,
) {
    /** The application whose installed plugins handle this request. */
    val application: Application get() = call.application

    /** Per-call attributes shared with HTTP lifecycle hooks. */
    val attributes: Attributes get() = call.attributes

    /** The original buffered HTTP request. */
    val request: HttpRequest get() = call.request

    /** Percent-decoded named path parameters from the matched route. */
    val param = InputValues(parameters::get)

    /** Decoded query parameters; `+` becomes a space and the first occurrence of each name is used. */
    val query = InputValues(queries::get)

    /** Request headers looked up by case-insensitive name; the first matching map entry is used. */
    val header = InputValues { name ->
        request.headers.entries.firstOrNull {
            it.key.equals(
                name,
                ignoreCase = true,
            )
        }?.value
    }

    /** The request body exposed as buffered text. */
    val body = RequestBody(request.body)

    /** Builds a UTF-8 plain-text response with [status]; a body-forbidding status rejects nonempty [body]. */
    fun text(body: String, status: HttpStatus = HttpStatus.OK): HttpResponse = dev.quwac.koyane.http.text(
        body,
        status,
    )

    /** Builds a response with [status], an empty body, and no headers. */
    fun empty(status: HttpStatus): HttpResponse = dev.quwac.koyane.http.empty(status)

    /** Builds an empty redirect response; invalid redirect statuses or locations throw [IllegalArgumentException]. */
    fun redirect(location: String, status: HttpStatus = HttpStatus.FOUND): HttpResponse =
        dev.quwac.koyane.http.redirect(location, status)
}

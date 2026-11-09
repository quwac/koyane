package dev.quwac.koyane.cors

import dev.quwac.koyane.http.HttpRequest

internal const val ALLOW_ORIGIN = "Access-Control-Allow-Origin"
internal const val ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials"
internal const val ALLOW_METHODS = "Access-Control-Allow-Methods"
internal const val ALLOW_HEADERS = "Access-Control-Allow-Headers"
internal const val EXPOSE_HEADERS = "Access-Control-Expose-Headers"
internal const val MAX_AGE = "Access-Control-Max-Age"
private const val ORIGIN = "Origin"
private const val REQUEST_METHOD = "Access-Control-Request-Method"
private const val REQUEST_HEADERS = "Access-Control-Request-Headers"
private const val VARY = "Vary"
private const val MAX_PORT = 65535
private const val BACKTICK = 96
private val managedHeaders = setOf(
    ALLOW_ORIGIN,
    ALLOW_CREDENTIALS,
    ALLOW_METHODS,
    ALLOW_HEADERS,
    EXPOSE_HEADERS,
    MAX_AGE,
)
    .map { it.lowercase() }.toSet()
private val originPattern = Regex("https?://(?:[a-z0-9._-]+|\\[[0-9a-f:.]+\\])(?::([0-9]+))?")

internal fun isCorsOrigin(value: String): Boolean {
    if (value == "null") return true
    val match = originPattern.matchEntire(value) ?: return false
    val port = match.groupValues[1]
    return port.isEmpty() || port.toIntOrNull()?.let { it in 0..MAX_PORT } == true
}

internal fun isCorsToken(value: String): Boolean = value.isNotEmpty() && value.all {
    it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "!#$%&'*+-.^_|~" || it.code == BACKTICK
}

internal fun checkedHeaderName(value: String): String {
    require(value != "*" && isCorsToken(value)) { "Expected an explicit HTTP header name" }
    return value.lowercase()
}

internal class CorsRequest(request: HttpRequest) {
    private val originValues = request.headers.valuesFor(ORIGIN)
    private val methodValues = request.headers.valuesFor(REQUEST_METHOD)
    val isOptions: Boolean = request.method == "OPTIONS"
    val isPreflight: Boolean = isOptions && originValues.isNotEmpty() && methodValues.isNotEmpty()
    val origin: String? = originValues.singleOrNull()
    val method: String? = methodValues.singleOrNull()
    val headers: Set<String>? = parseHeaders(request.headers.valuesFor(REQUEST_HEADERS))
    val isMalformed: Boolean = origin == null || !isCorsOrigin(origin) || method == null ||
        !isCorsToken(method) || headers == null
}

private fun Map<String, String>.valuesFor(name: String): List<String> = entries
    .filter { it.key.equals(name, ignoreCase = true) }.map { it.value.trim(' ', '\t') }

private fun parseHeaders(values: List<String>): Set<String>? {
    val tokens = values.flatMap { it.split(',') }.map { it.trim(' ', '\t') }
    return tokens.takeIf { it.all(::isCorsToken) }?.map { it.lowercase() }?.toSet()
}

/** Replace managed headers case-insensitively and preserve every existing Vary token. */
internal fun corsResponseHeaders(headers: Map<String, String>, options: Boolean): MutableMap<String, String> {
    val result = headers.filterKeys { it.lowercase() !in managedHeaders && !it.equals(VARY, ignoreCase = true) }
        .toMutableMap()
    val vary = headers.valuesFor(VARY).flatMap { it.split(',') }.map { it.trim(' ', '\t') }
        .filter { it.isNotEmpty() }
    val required = if (options) listOf(ORIGIN, REQUEST_METHOD, REQUEST_HEADERS) else listOf(ORIGIN)
    result[VARY] = if ("*" in vary) "*" else (vary + required).distinctBy { it.lowercase() }.joinToString(", ")
    return result
}

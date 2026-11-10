package dev.quwac.koyane.http

private const val MIN_FINAL_STATUS = 200
private const val MAX_FINAL_STATUS = 599
private val bodylessStatuses = listOf(204, 205, 304)

/**
 * A buffered text HTTP request. Input is validated when routed or extracted by a handler.
 *
 * @property method The case-sensitive HTTP method token used for route matching.
 * @property url An HTTP(S) URL or an absolute path with an optional query string.
 * @property headers Request header names and values; typed header lookup ignores name casing.
 * @property body The request body decoded as text by the host adapter.
 */
data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
)

/**
 * A final HTTP response with a buffered text body.
 *
 * @property status A final status code between 200 and 599.
 * @property body Response text; must be empty for statuses 204, 205, and 304.
 * @property headers Response header names and values passed to the host adapter.
 * @throws IllegalArgumentException if the status is outside the final-response range or forbids a nonempty body.
 */
data class HttpResponse(val status: Int, val body: String = "", val headers: Map<String, String> = emptyMap()) {
    init {
        require(status in MIN_FINAL_STATUS..MAX_FINAL_STATUS) { "Expected a final HTTP response status" }
        require(status !in bodylessStatuses || body.isEmpty()) {
            "This status cannot have a response body"
        }
    }
}

/**
 * A protocol-classified failure with a safe response for the HTTP lifecycle fallback.
 *
 * @property response The response returned if no error hook recovers this failure.
 */
open class HttpFailure(val response: HttpResponse) : RuntimeException("HTTP ${response.status}")

/**
 * Named HTTP statuses supported by the response builders and error-handling configuration.
 *
 * @property code The numeric HTTP status sent to the client.
 */
enum class HttpStatus(val code: Int) {
    /** 200: The request succeeded. */
    OK(200),

    /** 201: The request created a resource. */
    CREATED(201),

    /** 204: The request succeeded without a response body. */
    NO_CONTENT(204),

    /** 301: The resource has a permanent new location. */
    MOVED_PERMANENTLY(301),

    /** 302: The resource has a temporary alternative location. */
    FOUND(302),

    /** 303: The client should retrieve the indicated resource with a separate request. */
    SEE_OTHER(303),

    /** 307: A temporary redirect that preserves the request method. */
    TEMPORARY_REDIRECT(307),

    /** 308: A permanent redirect that preserves the request method. */
    PERMANENT_REDIRECT(308),

    /** 400: The request contains missing or invalid input. */
    BAD_REQUEST(400),

    /** 401: Valid authentication credentials are required. */
    UNAUTHORIZED(401),

    /** 404: No resource matches the request. */
    NOT_FOUND(404),

    /** 405: The path exists but does not support the request method. */
    METHOD_NOT_ALLOWED(405),

    /** 406: No available response format satisfies content negotiation. */
    NOT_ACCEPTABLE(406),

    /** 415: The request media type is unsupported. */
    UNSUPPORTED_MEDIA_TYPE(415),

    /** 500: An application failure prevented the request from completing. */
    INTERNAL_SERVER_ERROR(500),
}

/** Returns a UTF-8 plain-text response; [HttpResponse] validates whether [status] allows [body]. */
fun text(body: String, status: HttpStatus = HttpStatus.OK): HttpResponse =
    HttpResponse(status.code, body, mapOf("Content-Type" to "text/plain; charset=utf-8"))

/** Returns [status] with an empty body and no headers. */
fun empty(status: HttpStatus): HttpResponse = HttpResponse(status.code)

/**
 * Returns an empty redirect response with a `Location` header.
 *
 * @throws IllegalArgumentException unless [status] is 301, 302, 303, 307, or 308,
 * or if [location] is empty or contains a carriage return or newline.
 */
fun redirect(location: String, status: HttpStatus = HttpStatus.FOUND): HttpResponse {
    require(
        status in setOf(
            HttpStatus.MOVED_PERMANENTLY,
            HttpStatus.FOUND,
            HttpStatus.SEE_OTHER,
            HttpStatus.TEMPORARY_REDIRECT,
            HttpStatus.PERMANENT_REDIRECT,
        ),
    ) {
        "Expected a redirect status"
    }
    require(location.isNotEmpty() && location.none { it == '\r' || it == '\n' }) { "Invalid redirect location" }
    return HttpResponse(status.code, headers = mapOf("Location" to location))
}

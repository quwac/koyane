package dev.quwac.koyane.http

private const val MIN_FINAL_STATUS = 200
private const val MAX_FINAL_STATUS = 599
private val bodylessStatuses = listOf(204, 205, 304)

/** [url] is an HTTP(S) URL or an absolute path with an optional query string. */
data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
)

data class HttpResponse(val status: Int, val body: String = "", val headers: Map<String, String> = emptyMap()) {
    init {
        require(status in MIN_FINAL_STATUS..MAX_FINAL_STATUS) { "Expected a final HTTP response status" }
        require(status !in bodylessStatuses || body.isEmpty()) {
            "This status cannot have a response body"
        }
    }
}

/** A protocol-classified failure with a safe response for the HTTP lifecycle fallback. */
open class HttpFailure(val response: HttpResponse) : RuntimeException("HTTP ${response.status}")

enum class HttpStatus(val code: Int) {
    OK(200),
    CREATED(201),
    NO_CONTENT(204),
    MOVED_PERMANENTLY(301),
    FOUND(302),
    SEE_OTHER(303),
    TEMPORARY_REDIRECT(307),
    PERMANENT_REDIRECT(308),
    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    NOT_FOUND(404),
    METHOD_NOT_ALLOWED(405),
    NOT_ACCEPTABLE(406),
    UNSUPPORTED_MEDIA_TYPE(415),
    INTERNAL_SERVER_ERROR(500),
}

fun text(body: String, status: HttpStatus = HttpStatus.OK): HttpResponse =
    HttpResponse(status.code, body, mapOf("Content-Type" to "text/plain; charset=utf-8"))

fun empty(status: HttpStatus): HttpResponse = HttpResponse(status.code)

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

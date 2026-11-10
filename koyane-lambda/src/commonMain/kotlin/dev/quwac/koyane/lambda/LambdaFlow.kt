package dev.quwac.koyane.lambda

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.http.HttpRequest
import dev.quwac.koyane.http.HttpResponse
import dev.quwac.koyane.http.fetch
import kotlin.io.encoding.Base64

internal data class LambdaInput(
    val method: String,
    val path: String,
    val query: String,
    val headers: Map<String, String>,
    val body: String?,
    val base64Encoded: Boolean,
)

internal suspend fun Application.handleLambdaInput(input: LambdaInput): HttpResponse {
    val text = input.body.orEmpty().let { if (input.base64Encoded) Base64.decode(it).decodeToString() else it }
    val url = input.path + if (input.query.isEmpty()) "" else "?${input.query}"
    val response = fetch(HttpRequest(input.method, url, input.headers, text))
    return if (input.method == "HEAD") response.copy(body = "") else response
}

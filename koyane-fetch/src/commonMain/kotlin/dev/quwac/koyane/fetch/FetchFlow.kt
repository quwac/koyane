package dev.quwac.koyane.fetch

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpRequest

internal data class FetchInput(val method: String, val url: String, val headers: Map<String, String>, val body: String)

internal data class FetchOutput(val status: Int, val headers: Map<String, String>, val body: String?)

private val bodylessStatuses = setOf(204, 205, 304)

internal suspend fun Application.handleFetchInput(input: FetchInput): FetchOutput {
    val httpResponse = this[Http].handle(
        HttpRequest(input.method, input.url, input.headers, input.body),
    )
    return FetchOutput(
        status = httpResponse.status,
        headers = httpResponse.headers,
        body = httpResponse.body.takeUnless { httpResponse.status in bodylessStatuses || input.method == "HEAD" },
    )
}

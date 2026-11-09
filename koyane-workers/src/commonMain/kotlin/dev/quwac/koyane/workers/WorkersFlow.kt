package dev.quwac.koyane.workers

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpRequest

internal data class WorkerInput(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

internal data class WorkerOutput(val status: Int, val headers: Map<String, String>, val body: String?)

private val bodylessStatuses = setOf(204, 205, 304)

internal suspend fun Application.handleWorkerInput(input: WorkerInput): WorkerOutput {
    val httpResponse = this[Http].handle(
        HttpRequest(input.method, input.url, input.headers, input.body),
    )
    return WorkerOutput(
        status = httpResponse.status,
        headers = httpResponse.headers,
        body = httpResponse.body.takeUnless { httpResponse.status in bodylessStatuses },
    )
}

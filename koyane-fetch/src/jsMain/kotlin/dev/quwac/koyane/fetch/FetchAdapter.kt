@file:OptIn(ExperimentalJsExport::class)

package dev.quwac.koyane.fetch

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.runtime.nativePromise
import kotlinx.coroutines.await
import kotlin.js.ExperimentalJsExport
import kotlin.js.Promise

/** A native Fetch API Response. Host APIs are confined to this adapter. */
external interface FetchResponse {
    /** The numeric status of the native Fetch response. */
    val status: Int

    /** Reads and consumes the body as text; native body-read failures reject the promise. */
    fun text(): Promise<String>
}

/** The host's native Fetch Request. */
external interface FetchRequest {
    /** The native request method token. */
    val method: String

    /** The native request URL passed to HTTP routing. */
    val url: String

    /** The native request headers iterated by the adapter. */
    val headers: FetchHeaders

    /** Reads and consumes the body as text; native body-read failures reject the promise. */
    fun text(): Promise<String>
}

/** The host Headers view used to enumerate request header values. */
external interface FetchHeaders {
    /** Invokes [callback] with each header value followed by its name, using native Headers iteration. */
    fun forEach(callback: (String, String) -> Unit)
}

private external class Headers {
    constructor()
    fun set(name: String, value: String)
}

/** Bridges native Fetch requests and responses to an application with HTTP installed, buffering bodies as text. */
class FetchAdapter(private val application: Application) {
    /** Consumes [request] and resolves a native response; body-read, HTTP, and response-construction failures reject. */
    fun handle(request: FetchRequest): Promise<FetchResponse> = nativePromise { handleRequest(request) }

    /** Converts and handles a native request in the caller's coroutine, preserving its cancellation and resource scope. */
    suspend fun handleRequest(request: FetchRequest): FetchResponse {
        val headers = mutableMapOf<String, String>()
        request.headers.forEach { value, name -> headers[name] = value }
        val output = application.handleFetchInput(
            FetchInput(request.method, request.url, headers, request.text().await()),
        )
        return output.toFetchResponse()
    }
}

private fun FetchOutput.toFetchResponse(): FetchResponse {
    val nativeHeaders = Headers()
    headers.forEach { (name, value) -> nativeHeaders.set(name, value) }
    return createResponse(status, body, nativeHeaders)
}

// Parameters are referenced by the embedded JavaScript.
@Suppress("UnusedParameter")
private fun createResponse(status: Int, body: String?, headers: Headers): FetchResponse =
    js("new Response(body, { status, headers })")

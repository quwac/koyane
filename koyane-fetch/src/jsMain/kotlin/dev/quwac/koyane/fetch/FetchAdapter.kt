@file:OptIn(ExperimentalJsExport::class)

package dev.quwac.koyane.fetch

import dev.quwac.koyane.core.Application
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
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
    fun handle(request: FetchRequest): Promise<FetchResponse> = Promise { resolve, reject ->
        val operation: suspend () -> FetchResponse = {
            val headers = mutableMapOf<String, String>()
            request.headers.forEach { value, name -> headers[name] = value }
            val output = application.handleFetchInput(
                FetchInput(request.method, request.url, headers, request.text().awaitText()),
            )
            output.toFetchResponse()
        }
        operation.startCoroutine(object : Continuation<FetchResponse> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<FetchResponse>) {
                result.fold(resolve) { error -> reject(jsError(error.toString())) }
            }
        })
    }
}

private suspend fun Promise<String>.awaitText(): String = suspendCoroutine { continuation ->
    then { value ->
        continuation.resume(value)
        value
    }.catch { error ->
        continuation.resumeWithException(IllegalStateException(error.toString()))
        null
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

@Suppress("UnusedParameter")
private fun jsError(message: String): Throwable = js("new Error(message)")

@file:OptIn(kotlin.js.ExperimentalJsExport::class, kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.fetch

import dev.quwac.koyane.core.Application
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise

/** A native Fetch API Response. Host APIs are confined to this adapter. */
external interface FetchResponse : JsAny {
    val status: Int
    fun text(): Promise<JsString>
}

/** The host's native Fetch Request. */
external interface FetchRequest : JsAny {
    val method: String
    val url: String
    val headers: FetchHeaders
    fun text(): Promise<JsString>
}

external interface FetchHeaders : JsAny {
    fun forEach(callback: (String, String) -> Unit)
}

private external class Headers : JsAny {
    constructor()
    fun set(name: String, value: String)
}

class FetchAdapter(private val application: Application) {
    fun handle(request: FetchRequest): Promise<FetchResponse> = Promise { resolve, reject ->
        val operation: suspend () -> FetchResponse = {
            val headers = mutableMapOf<String, String>()
            request.headers.forEach { value, name -> headers[name] = value }
            val output = application.handleFetchInput(
                FetchInput(request.method, request.url, headers, request.readText()),
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

private suspend fun FetchRequest.readText(): String = suspendCoroutine { continuation ->
    text().then { value ->
        continuation.resume(value.toString())
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

@Suppress("UnusedParameter") // The parameter is referenced by the embedded JavaScript.
private fun jsError(message: String): JsAny = js("new Error(message)")

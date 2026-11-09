@file:OptIn(kotlin.js.ExperimentalJsExport::class, kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.workers

import dev.quwac.koyane.core.Application
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.js.JsAny
import kotlin.js.JsExport
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.unsafeCast

/** A native Fetch API Response. Host APIs are confined to this adapter. */
external interface WorkerResponse : JsAny {
    val status: Int
    fun text(): Promise<JsString>
}

/** The host's native Fetch Request. */
external interface WorkerRequest : JsAny {
    val method: String
    val url: String
    val headers: WorkerHeaders
    fun text(): Promise<JsString>
}

external interface WorkerHeaders : JsAny {
    fun forEach(callback: (String, String) -> Unit)
}

private external class Headers : JsAny {
    constructor()
    fun set(name: String, value: String)
}

class WorkersAdapter(private val application: Application) {
    fun handle(request: WorkerRequest): Promise<WorkerResponse> = Promise { resolve, reject ->
        val operation: suspend () -> WorkerResponse = {
            val headers = mutableMapOf<String, String>()
            request.headers.forEach { value, name -> headers[name] = value }
            val output = application.handleWorkerInput(
                WorkerInput(request.method, request.url, headers, request.readText()),
            )
            output.toWorkerResponse()
        }
        operation.startCoroutine(object : Continuation<WorkerResponse> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<WorkerResponse>) {
                result.fold(resolve) { error -> reject(jsError(error.toString())) }
            }
        })
    }
}

private var workerFactory: ((JsAny) -> Application)? = null
private var workerAdapter: WorkersAdapter? = null

/** Defines the single Koyane application hosted by this Worker module. */
fun <Environment : JsAny> defineWorker(applicationFactory: (Environment) -> Application) {
    check(workerFactory == null) { "A Worker is already defined" }
    workerFactory = { environment -> applicationFactory(environment.unsafeCast<Environment>()) }
}

/** The Fetch entry point exported to the JavaScript Worker bridge. */
@JsExport
fun fetch(request: WorkerRequest, environment: JsAny): Promise<WorkerResponse> {
    val adapter = workerAdapter ?: WorkersAdapter(
        checkNotNull(workerFactory) { "No Worker is defined; call defineWorker from main" }(environment),
    ).also { workerAdapter = it }
    return adapter.handle(request)
}

private suspend fun WorkerRequest.readText(): String = suspendCoroutine { continuation ->
    text().then { value ->
        continuation.resume(value.toString())
        value
    }.catch { error ->
        continuation.resumeWithException(IllegalStateException(error.toString()))
        null
    }
}

private fun WorkerOutput.toWorkerResponse(): WorkerResponse {
    val nativeHeaders = Headers()
    headers.forEach { (name, value) -> nativeHeaders.set(name, value) }
    return createResponse(status, body, nativeHeaders)
}

// Parameters are referenced by the embedded JavaScript.
@Suppress("UnusedParameter")
private fun createResponse(status: Int, body: String?, headers: Headers): WorkerResponse =
    js("new Response(body, { status, headers })")

@Suppress("UnusedParameter") // The parameter is referenced by the embedded JavaScript.
private fun jsError(message: String): JsAny = js("new Error(message)")

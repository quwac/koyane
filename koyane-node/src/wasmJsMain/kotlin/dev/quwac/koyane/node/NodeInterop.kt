@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.node

import dev.quwac.koyane.http.HttpRequest
import dev.quwac.koyane.http.HttpResponse
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.js.JsAny
import kotlin.js.JsModule
import kotlin.js.JsString
import kotlin.js.Promise

@JsModule("node:http")
private external object NodeHttp {
    @Suppress("UnusedParameter") // Implemented by Node.js.
    fun createServer(listener: (NativeRequest, NativeResponse) -> Unit): NativeServer
}

private external interface NativeRequest : JsAny {
    val method: String?
    val url: String?
}

private external interface NativeResponse : JsAny {
    var statusCode: Int
    val destroyed: Boolean
    fun setHeader(name: String, value: String)
    fun end(body: String)
}

private external interface NativeError : JsAny {
    val message: String
}

private external interface NativeAddress : JsAny {
    val port: Int
}

private external interface NativeServer : JsAny {
    fun once(event: String, callback: (NativeError) -> Unit)
    fun removeListener(event: String, callback: (NativeError) -> Unit)
    fun listen(port: Int, host: String, callback: () -> Unit)
    fun address(): NativeAddress
    fun close(callback: (NativeError?) -> Unit)
}

internal actual suspend fun startNodeServer(
    host: String,
    port: Int,
    handler: suspend (HttpRequest) -> HttpResponse,
): NodeServerHandle {
    val server = NodeHttp.createServer { request, response -> handleRequest(request, response, handler) }
    return suspendCoroutine { continuation ->
        val onError: (NativeError) -> Unit = { error ->
            continuation.resumeWithException(IllegalStateException(error.message))
        }
        server.once("error", onError)
        server.listen(port, host) {
            server.removeListener("error", onError)
            continuation.resume(ListeningServer(server))
        }
    }
}

private class ListeningServer(private val server: NativeServer) : NodeServerHandle {
    override val port = server.address().port

    override suspend fun close(): Unit = suspendCoroutine { continuation ->
        server.close { error ->
            if (error == null) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(IllegalStateException(error.message))
            }
        }
    }
}

private fun handleRequest(
    request: NativeRequest,
    response: NativeResponse,
    handler: suspend (HttpRequest) -> HttpResponse,
) {
    val operation: suspend () -> Unit = {
        val input = HttpRequest(
            method = request.method ?: "GET",
            url = request.url ?: "/",
            headers = requestHeaders(request),
            body = readBody(request).awaitText(),
        )
        val output = handler(input)
        if (!response.destroyed) {
            response.statusCode = output.status
            output.headers.forEach { (name, value) -> response.setHeader(name, value) }
            response.end(if (input.method == "HEAD") "" else output.body)
        }
    }
    operation.startCoroutine(object : Continuation<Unit> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(result: Result<Unit>) {
            result.exceptionOrNull()?.let { error ->
                println("Node HTTP request failed: $error")
                sendFailure(response)
            }
        }
    })
}

private fun requestHeaders(request: NativeRequest): Map<String, String> {
    val headers = linkedMapOf<String, String>()
    for (index in 0 until headerCount(request) step 2) {
        val name = rawHeader(request, index).lowercase()
        val value = rawHeader(request, index + 1)
        val separator = if (name == "cookie") "; " else ", "
        headers[name] = headers[name]?.let { "$it$separator$value" } ?: value
    }
    return headers
}

private suspend fun Promise<JsString>.awaitText(): String = suspendCoroutine { continuation ->
    then { value ->
        continuation.resume(value.toString())
        value
    }.catch { error ->
        continuation.resumeWithException(IllegalStateException(error.toString()))
        null
    }
}

@Suppress("UnusedParameter") // Parameters are referenced by the embedded JavaScript.
private fun headerCount(request: NativeRequest): Int = js("request.rawHeaders.length")

@Suppress("UnusedParameter")
private fun rawHeader(request: NativeRequest, index: Int): String = js("request.rawHeaders[index]")

@Suppress("UnusedParameter")
private fun readBody(request: NativeRequest): Promise<JsString> = js(
    """new Promise((resolve, reject) => {
        let body = '';
        request.setEncoding('utf8');
        request.on('data', chunk => { body += chunk; });
        request.once('end', () => resolve(body));
        request.once('error', reject);
        request.once('aborted', () => reject(new Error('Request aborted')));
    })""",
)

@Suppress("UnusedParameter")
private fun sendFailure(response: NativeResponse): Unit = js(
    """{
        if (!response.destroyed) {
            if (response.headersSent) {
                response.destroy();
            } else {
                for (const name of response.getHeaderNames()) response.removeHeader(name);
                response.statusCode = 500;
                response.setHeader('Content-Type', 'text/plain; charset=utf-8');
                response.end('Internal Server Error');
            }
        }
    }""",
)

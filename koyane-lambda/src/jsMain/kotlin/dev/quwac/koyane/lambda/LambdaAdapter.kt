@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.lambda

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpResponse
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.js.JsAny
import kotlin.js.Promise

/** API Gateway HTTP API / Lambda Function URL payload format 2.0. */
external interface LambdaHttpEvent : JsAny {
    /** The native payload format version; [LambdaAdapter] requires `2.0`. */
    val version: String?

    /** The absolute request path without the query string; required by [LambdaAdapter]. */
    val rawPath: String?

    /** The original query string without `?`; absence is treated as empty. */
    val rawQueryString: String?

    /** The native object of request header names and string values, when present. */
    val headers: JsAny?

    /** The native array of cookie strings, joined into the request Cookie header when nonempty. */
    val cookies: JsAny?

    /** The native text or Base64 request body; absence is treated as empty. */
    val body: String?

    /** Whether [body] contains Base64; absent or false leaves the body as text. */
    val isBase64Encoded: Boolean?

    /** The request context containing the HTTP method required for dispatch. */
    val requestContext: LambdaRequestContext?
}

/** The portion of the Lambda request context used to obtain HTTP request metadata. */
external interface LambdaRequestContext : JsAny {
    /** The native HTTP context, when supplied by the host. */
    val http: LambdaHttpContext?
}

/** The HTTP metadata used by the Lambda payload 2.0 adapter. */
external interface LambdaHttpContext : JsAny {
    /** The HTTP method required by [LambdaAdapter]. */
    val method: String?
}

/** Native Lambda HTTP payload 2.0 response, including the separate cookies array. */
external interface LambdaHttpResponse : JsAny {
    /** The final HTTP status code. */
    val statusCode: Int

    /** The native response header object; Set-Cookie values are placed in [cookies] instead. */
    val headers: JsAny

    /** The native array containing response Set-Cookie values. */
    val cookies: JsAny

    /** The buffered response text; HEAD responses have an empty body. */
    val body: String

    /** Whether the response body is Base64; this text adapter always returns false. */
    val isBase64Encoded: Boolean
}

/** Bridges HTTP payload 2.0 events with buffered text bodies; construction requires HTTP in [application]. */
class LambdaAdapter(private val application: Application) {
    init {
        application[Http]
    }

    /** Rejects unsupported event formats and failed HTTP invocations. */
    fun handle(event: LambdaHttpEvent): Promise<LambdaHttpResponse> = Promise { resolve, reject ->
        val operation: suspend () -> LambdaHttpResponse = {
            require(event.version == "2.0") { "Expected Lambda HTTP payload version 2.0" }
            val method = requireNotNull(event.requestContext?.http?.method) { "Expected an HTTP method" }
            val path = requireNotNull(event.rawPath) { "Expected rawPath" }
            require(path.startsWith("/")) { "Expected an absolute HTTP path" }
            val headers = eventHeaders(event)
            application.handleLambdaInput(
                LambdaInput(
                    method = method,
                    path = path,
                    query = event.rawQueryString.orEmpty(),
                    headers = headers,
                    body = event.body,
                    base64Encoded = event.isBase64Encoded == true,
                ),
            ).toLambdaResponse()
        }
        operation.startCoroutine(object : Continuation<LambdaHttpResponse> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<LambdaHttpResponse>) {
                result.fold(resolve) { error -> reject(jsError(error.toString())) }
            }
        })
    }
}

private fun eventHeaders(event: LambdaHttpEvent): Map<String, String> {
    val headers = linkedMapOf<String, String>()
    event.headers?.let { native ->
        for (index in 0 until headerCount(native)) {
            val name = headerName(native, index)
            headers[name.lowercase()] = headerValue(native, name)
        }
    }
    event.cookies?.let { cookies ->
        if (cookieCount(cookies) > 0) {
            headers["cookie"] = (0 until cookieCount(cookies)).joinToString("; ") { cookieAt(cookies, it) }
        }
    }
    return headers
}

private fun HttpResponse.toLambdaResponse(): LambdaHttpResponse {
    val output = createResponse(status, body)
    headers.forEach { (name, value) ->
        if (name.equals("set-cookie", ignoreCase = true)) {
            appendCookie(output, value)
        } else {
            setHeader(output, name, value)
        }
    }
    return output
}

@Suppress("UnusedParameter") // Parameters are referenced by the embedded JavaScript.
private fun headerCount(headers: JsAny): Int = js("Object.keys(headers).length")

@Suppress("UnusedParameter")
private fun headerName(headers: JsAny, index: Int): String = js("Object.keys(headers)[index]")

@Suppress("UnusedParameter")
private fun headerValue(headers: JsAny, name: String): String = js("headers[name]")

@Suppress("UnusedParameter")
private fun cookieCount(cookies: JsAny): Int = js("cookies.length")

@Suppress("UnusedParameter")
private fun cookieAt(cookies: JsAny, index: Int): String = js("cookies[index]")

@Suppress("UnusedParameter")
private fun createResponse(status: Int, body: String): LambdaHttpResponse =
    js("({ statusCode: status, headers: Object.create(null), cookies: [], body, isBase64Encoded: false })")

@Suppress("UnusedParameter")
private fun setHeader(output: LambdaHttpResponse, name: String, value: String): Unit =
    js("{ output.headers[name] = value; }")

@Suppress("UnusedParameter")
private fun appendCookie(output: LambdaHttpResponse, value: String): Unit = js("{ output.cookies.push(value); }")

@Suppress("UnusedParameter")
private fun jsError(message: String): Throwable = js("new Error(message)")

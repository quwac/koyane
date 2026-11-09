package dev.quwac.koyane.http

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.core.AttributeKey
import dev.quwac.koyane.core.Attributes
import dev.quwac.koyane.core.PluginScope

/** One call, including framework responses and failures, owns one attribute store. */
class HttpCall internal constructor(val application: Application, val request: HttpRequest) {
    val attributes = Attributes()
}

internal class HttpHooks {
    val request = mutableListOf<suspend HttpCall.() -> HttpResponse?>()
    val beforeHandler = mutableListOf<suspend HandlerScope.() -> HttpResponse?>()
    val response = mutableListOf<suspend HttpCall.(HttpResponse) -> HttpResponse>()
    val responseReady = mutableListOf<suspend HttpCall.(HttpResponse) -> Unit>()
    val error = mutableListOf<suspend HttpCall.(Throwable) -> HttpResponse?>()
}

private val hooksKey = AttributeKey<HttpHooks>("HTTP hooks")

internal fun Application.httpHooks(): HttpHooks? = attributes.getOrNull(hooksKey)

private fun PluginScope.httpHooks(): HttpHooks {
    val attributes = application.attributes
    return attributes.getOrNull(hooksKey) ?: HttpHooks().also { attributes[hooksKey] = it }
}

/** Runs before URL parsing. A response skips routing and the handler. */
fun PluginScope.onRequest(hook: suspend HttpCall.() -> HttpResponse?) {
    httpHooks().request += hook
}

/** Runs only for a matched method and route. A response skips the handler. */
fun PluginScope.beforeHandler(hook: suspend HandlerScope.() -> HttpResponse?) {
    httpHooks().beforeHandler += hook
}

/**
 * Transforms responses in registration order. Recovery restarts transformations except failed hooks.
 * Hooks can run again on a fresh recovered response; put one-time observations in [onResponseReady].
 */
fun PluginScope.onResponse(hook: suspend HttpCall.(HttpResponse) -> HttpResponse) {
    httpHooks().response += hook
}

/** Observes the final response once. Observer failures propagate without recovery or replay. */
fun PluginScope.onResponseReady(hook: suspend HttpCall.(HttpResponse) -> Unit) {
    httpHooks().responseReady += hook
}

/** The first non-null response recovers the failure; null continues to the next hook. */
fun PluginScope.onError(hook: suspend HttpCall.(Throwable) -> HttpResponse?) {
    httpHooks().error += hook
}

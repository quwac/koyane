package dev.quwac.koyane.http

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.core.Plugin
import dev.quwac.koyane.core.PluginScope
import dev.quwac.koyane.core.koyane
import kotlin.coroutines.cancellation.CancellationException

object Http : Plugin<HttpConfiguration, HttpApplication> {
    override fun createConfiguration(): HttpConfiguration = HttpConfiguration()
    override fun install(configuration: HttpConfiguration): HttpApplication = configuration.build()
    override fun onInstall(scope: PluginScope, extension: HttpApplication) {
        extension.application = scope.application
    }
}

enum class HttpMethod { GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS, QUERY }

class HttpConfiguration internal constructor() {
    private val handlers = mutableListOf<RegisteredHandler>()

    fun route(method: HttpMethod, path: String, handler: suspend HandlerScope.() -> HttpResponse) {
        val pattern = RoutePattern(path)
        require(
            handlers.none { it.method == method && it.pattern.shape == pattern.shape },
        ) { "Duplicate route: $method $path" }
        handlers += RegisteredHandler(method, pattern, handler)
    }

    fun get(path: String, handler: suspend HandlerScope.() -> HttpResponse) = route(HttpMethod.GET, path, handler)
    fun query(path: String, handler: suspend HandlerScope.() -> HttpResponse) = route(HttpMethod.QUERY, path, handler)
    fun post(path: String, handler: suspend HandlerScope.() -> HttpResponse) = route(HttpMethod.POST, path, handler)
    fun put(path: String, handler: suspend HandlerScope.() -> HttpResponse) = route(HttpMethod.PUT, path, handler)
    fun patch(path: String, handler: suspend HandlerScope.() -> HttpResponse) = route(HttpMethod.PATCH, path, handler)
    fun delete(path: String, handler: suspend HandlerScope.() -> HttpResponse) = route(HttpMethod.DELETE, path, handler)
    fun head(path: String, handler: suspend HandlerScope.() -> HttpResponse) = route(HttpMethod.HEAD, path, handler)
    fun options(path: String, handler: suspend HandlerScope.() -> HttpResponse) = route(
        HttpMethod.OPTIONS,
        path,
        handler,
    )

    internal fun build(): HttpApplication = HttpApplication(handlers.sortedBy { it.pattern.priority })
}

suspend fun Application.fetch(request: HttpRequest): HttpResponse = this[Http].handle(request)

internal class RegisteredHandler(
    val method: HttpMethod,
    val pattern: RoutePattern,
    val handler: suspend HandlerScope.() -> HttpResponse,
)

class HttpApplication internal constructor(private val handlers: List<RegisteredHandler>) {
    internal var application: Application? = null

    @Suppress("TooGenericExceptionCaught") // This boundary delegates application failures to error hooks.
    suspend fun handle(request: HttpRequest): HttpResponse {
        val owner = application ?: koyane {}.also { application = it }
        val call = HttpCall(owner, request)
        val hooks = owner.httpHooks()
        val response = try {
            receive(call, hooks)
        } catch (cause: Throwable) {
            recover(call, hooks, cause)
        }
        val ready = respond(call, hooks, response)
        hooks?.responseReady?.forEach { hook -> hook(call, ready) }
        return ready
    }

    @Suppress("TooGenericExceptionCaught") // Failed transformations are excluded from this call's recovery pass.
    private suspend fun respond(call: HttpCall, hooks: HttpHooks?, response: HttpResponse): HttpResponse {
        val transforms = hooks?.response ?: return response
        if (transforms.isEmpty()) return response
        val failed = mutableSetOf<Int>()
        var current = response
        var index = 0
        while (index < transforms.size) {
            val next = index++
            if (next !in failed) {
                try {
                    current = transforms[next](call, current)
                } catch (cause: Throwable) {
                    failed += next
                    current = recover(call, hooks, cause)
                    index = 0
                }
            }
        }
        return current
    }

    private suspend fun receive(call: HttpCall, hooks: HttpHooks?): HttpResponse =
        hooks?.request?.firstNotNullOfOrNull { it(call) } ?: dispatch(call, hooks)

    private suspend fun dispatch(call: HttpCall, hooks: HttpHooks?): HttpResponse {
        val target = parseTarget(call.request.url) ?: invalidHttpInput()
        val matches = handlers.mapNotNull { registered ->
            registered.pattern.match(target.segments)?.let { registered to it }
        }
        val matched = matches.firstOrNull { (registered, _) -> registered.method.name == call.request.method }
        return when {
            matched != null -> {
                val (registered, parameters) = matched
                val scope = HandlerScope(call, parameters, target.query)
                hooks?.beforeHandler?.firstNotNullOfOrNull { hook -> hook(scope) } ?: registered.handler(scope)
            }

            matches.isNotEmpty() -> httpFailure(
                HttpResponse(
                    status = HttpStatus.METHOD_NOT_ALLOWED.code,
                    headers = mapOf(
                        "Allow" to matches.map { it.first.method.name }.distinct().sorted().joinToString(", "),
                    ),
                ),
            )

            else -> httpFailure(empty(HttpStatus.NOT_FOUND))
        }
    }

    private suspend fun recover(call: HttpCall, hooks: HttpHooks?, cause: Throwable): HttpResponse {
        if (cause is CancellationException) throw cause
        hooks?.error?.forEach { hook -> hook(call, cause)?.let { return it } }
        if (cause is HttpFailure) return cause.response
        throw cause
    }
}

private fun invalidHttpInput(): Nothing = throw InvalidHttpInput()

private fun httpFailure(response: HttpResponse): Nothing = throw HttpFailure(response)

package dev.quwac.koyane.http

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.core.Plugin
import dev.quwac.koyane.core.PluginScope
import dev.quwac.koyane.core.koyane
import kotlin.coroutines.cancellation.CancellationException

/** Installs HTTP routing and the request, response, and error lifecycle into an application. */
object Http : Plugin<HttpConfiguration, HttpApplication> {
    override fun createConfiguration(): HttpConfiguration = HttpConfiguration()
    override fun install(configuration: HttpConfiguration): HttpApplication = configuration.build()
    override fun onInstall(scope: PluginScope, extension: HttpApplication) {
        extension.application = scope.application
    }
}

/** HTTP methods accepted by route registration; request method matching is case-sensitive. */
enum class HttpMethod {
    /** Retrieves a representation. */
    GET,

    /** Submits content for processing. */
    POST,

    /** Creates or replaces a resource representation. */
    PUT,

    /** Applies a partial resource modification. */
    PATCH,

    /** Removes a resource. */
    DELETE,

    /** Retrieves response headers; host adapters omit the response body. */
    HEAD,

    /** Requests communication options for a resource. */
    OPTIONS,

    /** Performs a query with request content. */
    QUERY,
}

/** Registers named routes when installing [Http]. Static segments take priority over parameters. */
class HttpConfiguration internal constructor() {
    private val handlers = mutableListOf<RegisteredHandler>()

    /**
     * Registers [handler] for [method] and an unencoded absolute [path], such as `/users/:id` or `/users/{id}`.
     * Parameter names must be unique and match `[A-Za-z_][A-Za-z0-9_]*`.
     * Trailing slashes are allowed only for the root path `/`.
     *
     * @throws IllegalArgumentException if the path is invalid or the same method and route shape are registered twice.
     */
    fun route(method: HttpMethod, path: String, handler: suspend HandlerScope.() -> HttpResponse) {
        val pattern = RoutePattern(path)
        require(
            handlers.none { it.method == method && it.pattern.shape == pattern.shape },
        ) { "Duplicate route: $method $path" }
        handlers += RegisteredHandler(method, pattern, handler)
    }

    /** Registers a GET handler using the path syntax and validation of [route]. */
    fun get(path: String, handler: suspend HandlerScope.() -> HttpResponse) = route(HttpMethod.GET, path, handler)

    /** Registers a QUERY handler using the path syntax and validation of [route]. */
    fun query(path: String, handler: suspend HandlerScope.() -> HttpResponse) = route(HttpMethod.QUERY, path, handler)

    /** Registers a POST handler using the path syntax and validation of [route]. */
    fun post(path: String, handler: suspend HandlerScope.() -> HttpResponse) = route(HttpMethod.POST, path, handler)

    /** Registers a PUT handler using the path syntax and validation of [route]. */
    fun put(path: String, handler: suspend HandlerScope.() -> HttpResponse) = route(HttpMethod.PUT, path, handler)

    /** Registers a PATCH handler using the path syntax and validation of [route]. */
    fun patch(path: String, handler: suspend HandlerScope.() -> HttpResponse) = route(HttpMethod.PATCH, path, handler)

    /** Registers a DELETE handler using the path syntax and validation of [route]. */
    fun delete(path: String, handler: suspend HandlerScope.() -> HttpResponse) = route(HttpMethod.DELETE, path, handler)

    /** Registers an explicit HEAD handler using [route]; GET handlers do not automatically handle HEAD. */
    fun head(path: String, handler: suspend HandlerScope.() -> HttpResponse) = route(HttpMethod.HEAD, path, handler)

    /** Registers an OPTIONS handler using the path syntax and validation of [route]. */
    fun options(path: String, handler: suspend HandlerScope.() -> HttpResponse) = route(
        HttpMethod.OPTIONS,
        path,
        handler,
    )

    internal fun build(): HttpApplication = HttpApplication(handlers.sortedBy { it.pattern.priority })
}

/** Dispatches [request] through the installed [Http] plugin; throws [IllegalStateException] if it is absent. */
suspend fun Application.fetch(request: HttpRequest): HttpResponse = this[Http].handle(request)

internal class RegisteredHandler(
    val method: HttpMethod,
    val pattern: RoutePattern,
    val handler: suspend HandlerScope.() -> HttpResponse,
)

/** Installed HTTP routes and lifecycle dispatch for one application. */
class HttpApplication internal constructor(private val handlers: List<RegisteredHandler>) {
    internal var application: Application? = null

    /**
     * Runs request hooks, route selection, the handler, response transformations, and final response observers.
     * Unmatched paths produce 404; unsupported methods on a matched path produce 405 with `Allow`.
     * [HttpFailure] supplies the fallback response. Unhandled application failures and cancellation propagate.
     */
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

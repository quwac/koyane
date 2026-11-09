package dev.quwac.koyane.errorhandling

import dev.quwac.koyane.core.Plugin
import dev.quwac.koyane.core.PluginScope
import dev.quwac.koyane.http.HttpCall
import dev.quwac.koyane.http.HttpFailure
import dev.quwac.koyane.http.HttpResponse
import dev.quwac.koyane.http.HttpStatus
import dev.quwac.koyane.http.empty
import dev.quwac.koyane.http.onError
import kotlin.coroutines.cancellation.CancellationException

/** Configures first-match exception mappings and exact HTTP status mappings. */
class ErrorHandlingConfiguration {
    private val exceptions = mutableListOf<ExceptionMapping>()
    private val statuses = mutableMapOf<HttpStatus, suspend HttpCall.() -> HttpResponse>()

    /**
     * Registers a type-safe exception mapping without reflection.
     *
     * Application exception mappings use first-match order. Register specific exception types before [Throwable].
     * Duplicate exception conditions are allowed and the first matching registration wins. Protocol-classified
     * [HttpFailure] instances use [status] mappings instead.
     */
    inline fun <reified T : Throwable> exception(noinline handler: suspend HttpCall.(T) -> HttpResponse) {
        registerException(
            matches = { cause -> cause is T },
            handler = { cause -> handler(cause as T) },
        )
    }

    /**
     * Registers one response for a framework HTTP status.
     *
     * The handler must return the registered status. Duplicate status registrations are rejected.
     */
    fun status(status: HttpStatus, handler: suspend HttpCall.() -> HttpResponse) {
        require(status !in statuses) { "A handler is already registered for HTTP ${status.code}" }
        statuses[status] = handler
    }

    @PublishedApi
    internal fun registerException(
        matches: (Throwable) -> Boolean,
        handler: suspend HttpCall.(Throwable) -> HttpResponse,
    ) {
        exceptions += ExceptionMapping(matches, handler)
    }

    internal fun build(): ErrorHandlingPolicy = ErrorHandlingPolicy(exceptions.toList(), statuses.toMap())
}

/** Immutable error mappings owned by one installed application. */
class ErrorHandlingPolicy internal constructor(
    private val exceptions: List<ExceptionMapping>,
    private val statuses: Map<HttpStatus, suspend HttpCall.() -> HttpResponse>,
) {
    @Suppress("TooGenericExceptionCaught") // A failing user mapping must terminate in one safe response.
    internal suspend fun resolve(call: HttpCall, cause: Throwable): HttpResponse {
        if (cause is CancellationException) throw cause
        return try {
            val exception = if (cause is HttpFailure) null else exceptions.firstOrNull { it.matches(cause) }
            if (cause is HttpFailure) {
                status(call, cause.response)
            } else if (exception != null) {
                exception.handler(call, cause)
            } else {
                status(call, empty(HttpStatus.INTERNAL_SERVER_ERROR))
            }
        } catch (handlerFailure: Throwable) {
            if (handlerFailure is CancellationException) throw handlerFailure
            if (handlerFailure is HttpFailure) handlerFailure.response else empty(HttpStatus.INTERNAL_SERVER_ERROR)
        }
    }

    private suspend fun status(call: HttpCall, fallback: HttpResponse): HttpResponse {
        val status = HttpStatus.entries.firstOrNull { it.code == fallback.status } ?: return fallback
        val handler = statuses[status] ?: return fallback
        val response = handler(call)
        check(response.status == status.code) {
            "A handler for HTTP ${status.code} returned HTTP ${response.status}"
        }
        return response.withFallbackHeaders(fallback.headers)
    }
}

/** Converts failures reaching the HTTP error hook into explicit responses. */
object ErrorHandling : Plugin<ErrorHandlingConfiguration, ErrorHandlingPolicy> {
    override fun createConfiguration(): ErrorHandlingConfiguration = ErrorHandlingConfiguration()
    override fun install(configuration: ErrorHandlingConfiguration): ErrorHandlingPolicy = configuration.build()

    override fun onInstall(scope: PluginScope, extension: ErrorHandlingPolicy) {
        scope.onError { cause -> extension.resolve(this, cause) }
    }
}

internal class ExceptionMapping(
    val matches: (Throwable) -> Boolean,
    val handler: suspend HttpCall.(Throwable) -> HttpResponse,
)

private fun HttpResponse.withFallbackHeaders(fallback: Map<String, String>): HttpResponse {
    if (fallback.isEmpty()) return this
    val merged = fallback.toMutableMap()
    for ((name, value) in headers) {
        merged.keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(merged::remove)
        merged[name] = value
    }
    return copy(headers = merged)
}

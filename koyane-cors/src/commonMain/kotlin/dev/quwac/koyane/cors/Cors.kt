package dev.quwac.koyane.cors

import dev.quwac.koyane.core.Plugin
import dev.quwac.koyane.core.PluginScope
import dev.quwac.koyane.http.HttpMethod
import dev.quwac.koyane.http.HttpResponse
import dev.quwac.koyane.http.HttpStatus
import dev.quwac.koyane.http.onRequest
import dev.quwac.koyane.http.onResponse

class CorsConfiguration {
    private val origins = mutableSetOf<String>()
    private val methods = mutableSetOf(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST)
    private val headers = mutableSetOf<String>()
    private val exposed = mutableSetOf<String>()
    private var anyOrigin = false

    var allowCredentials: Boolean = false
    var maxAgeSeconds: Long = 0

    /** An exact serialized ASCII HTTP(S) origin, without a path or trailing slash. */
    fun allowOrigin(origin: String) {
        require(origin != "null" && isCorsOrigin(origin)) { "Expected a serialized HTTP(S) origin" }
        origins += origin
    }

    fun allowAnyOrigin() {
        anyOrigin = true
    }

    fun allowMethod(method: HttpMethod) {
        methods += method
    }

    fun allowHeader(name: String) {
        headers += checkedHeaderName(name)
    }

    fun exposeHeader(name: String) {
        exposed += checkedHeaderName(name)
    }

    internal fun build(): CorsPolicy {
        require(!anyOrigin || !allowCredentials) { "Wildcard origins cannot allow credentials" }
        require(maxAgeSeconds >= 0) { "CORS max age must be non-negative" }
        return CorsPolicy(
            origins.toSet(),
            anyOrigin,
            methods.map { it.name }.toSet(),
            headers.toSet(),
            exposed.toSet(),
            allowCredentials,
            maxAgeSeconds,
        )
    }
}

/** An immutable, application-owned CORS policy. */
class CorsPolicy internal constructor(
    private val origins: Set<String>,
    private val anyOrigin: Boolean,
    private val methods: Set<String>,
    private val headers: Set<String>,
    private val exposed: Set<String>,
    private val credentials: Boolean,
    private val maxAgeSeconds: Long,
) {
    internal fun preflight(request: CorsRequest): HttpResponse? {
        if (!request.isPreflight) return null
        val status = when {
            request.isMalformed -> HttpStatus.BAD_REQUEST.code

            allowedOrigin(request.origin) == null || request.method !in methods ||
                !headers.containsAll(checkNotNull(request.headers)) -> FORBIDDEN

            else -> HttpStatus.NO_CONTENT.code
        }
        return HttpResponse(status)
    }

    internal fun respond(request: CorsRequest, response: HttpResponse): HttpResponse {
        val result = corsResponseHeaders(response.headers, request.isOptions)
        val origin = allowedOrigin(request.origin)
        if (origin != null && !request.isPreflight) {
            result.putAll(grant(origin))
            if (exposed.isNotEmpty()) result[EXPOSE_HEADERS] = exposed.joinToString(", ")
        } else if (request.isPreflight && preflight(request)?.status == HttpStatus.NO_CONTENT.code &&
            response.status in successStatuses
        ) {
            result.putAll(grant(checkNotNull(origin)))
            result[ALLOW_METHODS] = methods.joinToString(", ")
            if (!request.headers.isNullOrEmpty()) result[ALLOW_HEADERS] = request.headers.sorted().joinToString(", ")
            result[MAX_AGE] = maxAgeSeconds.toString()
        }
        return response.copy(headers = result)
    }

    private fun allowedOrigin(origin: String?): String? = when {
        origin == null || !isCorsOrigin(origin) -> null
        anyOrigin -> "*"
        origin in origins -> origin
        else -> null
    }

    private fun grant(origin: String): Map<String, String> = buildMap {
        put(ALLOW_ORIGIN, origin)
        if (credentials) put(ALLOW_CREDENTIALS, "true")
    }
}

object Cors : Plugin<CorsConfiguration, CorsPolicy> {
    override fun createConfiguration(): CorsConfiguration = CorsConfiguration()
    override fun install(configuration: CorsConfiguration): CorsPolicy = configuration.build()

    override fun onInstall(scope: PluginScope, extension: CorsPolicy) {
        scope.onRequest { extension.preflight(CorsRequest(request)) }
        scope.onResponse { response -> extension.respond(CorsRequest(request), response) }
    }
}

private const val FORBIDDEN = 403
private val successStatuses = 200..299

package dev.quwac.koyane.node

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpRequest
import dev.quwac.koyane.http.HttpResponse
import dev.quwac.koyane.http.fetch
import dev.quwac.koyane.runtime.Environment
import dev.quwac.koyane.runtime.ResourceScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val MAX_PORT = 65535

/** A listening Node.js HTTP server. The application is shared across concurrent requests. */
class NodeServer internal constructor(
    private val handle: NodeServerHandle,
    /** The local interface used when opening this server. */
    val host: String,
) {
    internal var resources: ResourceScope? = null
    private val closing = Mutex()

    /** The bound port, including the assigned port when listening on port zero. */
    val port: Int get() = handle.port

    /** Stops accepting connections and waits for active requests to finish. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun close() = withContext(NonCancellable) {
        closing.withLock {
            var failure: Throwable? = null
            try {
                handle.close()
            } catch (cause: Throwable) {
                failure = cause
                throw cause
            } finally {
                resources?.close(failure)
            }
        }
    }
}

/** Starts a Node.js HTTP server and returns after it is listening. Port zero requests an available port. */
suspend fun Application.listen(host: String = "127.0.0.1", port: Int = 3000): NodeServer {
    require(host.isNotBlank()) { "Expected a non-empty listen host" }
    require(port in 0..MAX_PORT) { "Expected a TCP port between 0 and $MAX_PORT" }
    this[Http]
    return NodeServer(startNodeServer(host, port) { request -> fetch(request) }, host)
}

internal interface NodeServerHandle {
    val port: Int
    suspend fun close()
}

internal expect suspend fun startNodeServer(
    host: String,
    port: Int,
    handler: suspend (HttpRequest) -> HttpResponse,
): NodeServerHandle

/** Server configuration and resources, retained until the listening server closes. */
class NodeServerScope internal constructor(
    /** The local interface to bind. */
    var host: String,
    /** TCP port; zero requests an available port. */
    var port: Int,
) : ResourceScope()

/** Creates one application with the process environment and releases its resources after active requests finish. */
@Suppress("TooGenericExceptionCaught")
suspend fun nodeServer(
    host: String = "127.0.0.1",
    port: Int = 3000,
    applicationFactory: suspend NodeServerScope.(Environment) -> Application,
): NodeServer {
    val scope = NodeServerScope(host, port)
    try {
        val application = applicationFactory(scope, nodeEnvironment())
        val server = application.listen(scope.host, scope.port)
        server.resources = scope
        return server
    } catch (cause: Throwable) {
        scope.close(cause)
        throw cause
    }
}

internal expect fun nodeEnvironment(): Environment

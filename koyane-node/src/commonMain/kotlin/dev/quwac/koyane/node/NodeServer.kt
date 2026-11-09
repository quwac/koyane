package dev.quwac.koyane.node

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpRequest
import dev.quwac.koyane.http.HttpResponse
import dev.quwac.koyane.http.fetch

private const val MAX_PORT = 65535

/** A listening Node.js HTTP server. The application is shared across concurrent requests. */
class NodeServer internal constructor(private val handle: NodeServerHandle) {
    /** The bound port, including the assigned port when listening on port zero. */
    val port: Int get() = handle.port

    /** Stops accepting connections and waits for active requests to finish. */
    suspend fun close() = handle.close()
}

/** Starts a Node.js HTTP server and returns after it is listening. Port zero requests an available port. */
suspend fun Application.listen(host: String = "127.0.0.1", port: Int = 3000): NodeServer {
    require(host.isNotBlank()) { "Expected a non-empty listen host" }
    require(port in 0..MAX_PORT) { "Expected a TCP port between 0 and $MAX_PORT" }
    this[Http]
    return NodeServer(startNodeServer(host, port) { request -> fetch(request) })
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

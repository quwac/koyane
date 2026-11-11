package dev.quwac.koyane.example.lambdaweb

import dev.quwac.koyane.node.nodeServer

/** Starts the example at AWS_LAMBDA_HTTP_ENDPOINT, or `127.0.0.1:3000` when the variable is absent. */
suspend fun main() {
    val server = nodeServer { environment ->
        val endpoint = environment.getStringOrNull("AWS_LAMBDA_HTTP_ENDPOINT") ?: "127.0.0.1:3000"
        val separator = endpoint.lastIndexOf(':')
        require(separator > 0) { "Expected AWS_LAMBDA_HTTP_ENDPOINT in host:port format" }
        host = endpoint.substring(0, separator).removeSurrounding("[", "]")
        port = endpoint.substring(separator + 1).toInt()
        createApplication()
    }
    println("Listening on ${server.host}:${server.port}")
}

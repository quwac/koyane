package dev.quwac.koyane.example.lambdaweb

import dev.quwac.koyane.node.listen

suspend fun main() {
    val endpoint = lambdaHttpEndpoint() ?: "127.0.0.1:3000"
    val separator = endpoint.lastIndexOf(':')
    require(separator > 0) { "Expected AWS_LAMBDA_HTTP_ENDPOINT in host:port format" }
    val host = endpoint.substring(0, separator).removeSurrounding("[", "]")
    val port = endpoint.substring(separator + 1).toInt()
    val server = createApplication().listen(host, port)
    println("Listening on $host:${server.port}")
}

internal expect fun lambdaHttpEndpoint(): String?

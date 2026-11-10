package dev.quwac.koyane.logging

import dev.quwac.koyane.core.Plugin
import dev.quwac.koyane.core.PluginScope
import dev.quwac.koyane.http.onError
import dev.quwac.koyane.http.onRequest
import dev.quwac.koyane.http.onResponseReady

/** Selects the output function used by the installed [Logging] plugin. */
class LoggingConfiguration {
    /** Receives each log message synchronously; defaults to [println] and any sink failure propagates. */
    var sink: (String) -> Unit = ::println
}

/** An application-owned logger with an explicitly supplied output function. */
class Logger internal constructor(private val sink: (String) -> Unit) {
    /** Passes [message] unchanged to the configured sink; sink failures propagate to the caller. */
    fun log(message: String) = sink(message)
}

/** Logs request paths, final response statuses, and failures; query strings and fragments are omitted. */
object Logging : Plugin<LoggingConfiguration, Logger> {
    override fun createConfiguration(): LoggingConfiguration = LoggingConfiguration()
    override fun install(configuration: LoggingConfiguration): Logger = Logger(configuration.sink)

    override fun onInstall(scope: PluginScope, extension: Logger) {
        scope.onRequest {
            extension.log("Request ${request.method} ${path(request.url)}")
            null
        }
        scope.onResponseReady { response ->
            extension.log("Response ${request.method} ${path(request.url)} ${response.status}")
        }
        scope.onError { cause ->
            extension.log("Error ${request.method} ${path(request.url)}: ${cause.message}")
            null
        }
    }
}

private fun path(url: String): String {
    val target = url.substringBefore('?').substringBefore('#')
    return if (target.startsWith('/')) target else "/" + target.substringAfter("://").substringAfter('/', "")
}

package dev.quwac.koyane.runtime

import dev.quwac.koyane.core.Application

/** Creates an application and owns its resources for each invocation, independently across concurrent calls. */
class RequestApplication(private val factory: suspend ResourceScope.(Environment) -> Application) {
    /** Runs buffered response processing and releases resources on success, failure, or cancellation. */
    suspend fun <T> handle(environment: Environment, respond: suspend (Application) -> T): T =
        withResourceScope { respond(factory(environment)) }
}

package dev.quwac.koyane.example

import dev.quwac.kagiroi.d1.CloudflareD1Binding
import dev.quwac.kagiroi.d1.D1Database
import dev.quwac.koyane.workers.defineWorker

private external interface ExampleEnvironment {
    @Suppress("VariableNaming", "PropertyName") // Matches the Wrangler binding.
    val DB: CloudflareD1Binding
}

/** Registers the Worker application factory using the host environment's D1 binding. */
fun main() = defineWorker<ExampleEnvironment> { environment ->
    createApplication(D1Database(environment.DB))
}

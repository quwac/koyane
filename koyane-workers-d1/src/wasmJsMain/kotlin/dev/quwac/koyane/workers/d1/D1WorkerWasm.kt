package dev.quwac.koyane.workers.d1

import dev.quwac.kagiroi.d1.CloudflareD1Binding
import dev.quwac.kagiroi.d1.D1Database
import dev.quwac.kagiroi.d1.D1DatabaseConfiguration
import dev.quwac.koyane.core.Application
import dev.quwac.koyane.workers.defineWorker

actual fun defineD1Worker(
    configureDatabase: D1DatabaseConfiguration.() -> Unit,
    applicationFactory: (D1Database) -> Application,
) = defineWorker<CloudflareD1Binding> { binding ->
    applicationFactory(D1Database(binding, configureDatabase))
}

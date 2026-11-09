package dev.quwac.koyane.workers.d1

import dev.quwac.kagiroi.d1.D1Database
import dev.quwac.kagiroi.d1.D1DatabaseConfiguration
import dev.quwac.koyane.core.Application

/** Defines a Worker application backed by the host's D1 binding. */
expect fun defineD1Worker(
    configureDatabase: D1DatabaseConfiguration.() -> Unit = {},
    applicationFactory: (D1Database) -> Application,
)

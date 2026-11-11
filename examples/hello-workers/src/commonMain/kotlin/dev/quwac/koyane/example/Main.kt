package dev.quwac.koyane.example

import dev.quwac.kagiroi.d1.D1Binding
import dev.quwac.kagiroi.d1.D1Database
import dev.quwac.koyane.cloudflare.workers.cloudflareWorker

/** Registers the application and its request-owned D1 database using the host environment. */
fun main() {
    cloudflareWorker { environment ->
        val database = resource({ D1Database(environment.get("DB", ::D1Binding)) }) { it.close() }
        createApplication(database)
    }
}

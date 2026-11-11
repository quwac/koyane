package dev.quwac.koyane.example

import dev.quwac.koyane.cloudflare.workers.cloudflareWorker
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import kotlinx.coroutines.delay

private const val HANDLER_DELAY_MILLIS = 10L
private var acquired = 0
private val released = mutableSetOf<Int>()

/** Integration entry point for a Cloudflare Worker configured entirely in common code. */
fun main() {
    cloudflareWorker { environment ->
        val greeting = environment.getString("GREETING")
        environment.get("DB")
        val id = resource({ ++acquired }) { released += it }
        koyane {
            install(Http) {
                get("/hello") {
                    delay(HANDLER_DELAY_MILLIS)
                    check(id !in released)
                    text("$greeting/$id")
                }
                get("/released") { text(released.sorted().joinToString(",")) }
                get("/failure") { error("request failure") }
            }
        }
    }
}

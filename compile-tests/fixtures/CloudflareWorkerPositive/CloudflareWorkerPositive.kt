import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.cloudflare.workers.cloudflareWorker

fun main() {
    cloudflareWorker { environment ->
        val value = resource({ environment.get("CONFIG").getString("GREETING") }) { }
        koyane { install(Http) { get("/hello") { text(value) } } }
    }
}

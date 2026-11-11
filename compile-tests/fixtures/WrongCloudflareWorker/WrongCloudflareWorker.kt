import dev.quwac.koyane.cloudflare.workers.cloudflareWorker

fun main() {
    cloudflareWorker { environment -> environment.getString("GREETING") }
}

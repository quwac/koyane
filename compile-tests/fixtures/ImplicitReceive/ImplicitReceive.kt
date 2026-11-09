package fixtures

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.serialization.kotlinx.receive

val app = koyane {
    install(Http) {
        post("/users") {
            val user: User = receive()
            text(user.id.toString())
        }
    }
}

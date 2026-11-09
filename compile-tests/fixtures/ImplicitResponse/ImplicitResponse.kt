package fixtures

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.serialization.kotlinx.respond

val app = koyane {
    install(Http) {
        get("/users") { respond(User(1)) }
    }
}

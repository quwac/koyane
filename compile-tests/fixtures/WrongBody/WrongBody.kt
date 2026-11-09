package fixtures

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http

val app = koyane {
    install(Http) {
        get("/users/{id}") { text(User(param.long("id"))) }
    }
}

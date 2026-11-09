package fixtures

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http

val app = koyane {
    install(Http) {
        get("/users/{id}") { getUser(param.long("id")) }
    }
}

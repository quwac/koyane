package fixtures

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http

val app = koyane {
    install(Http) {
        get("/users/{id}") {
            when (val result = getUser(param.long("id"))) {
                is GetUserResponse.Ok -> text(result.body.id.toString())
            }
        }
    }
}

package fixtures

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.authentication.bearer.BearerAuthentication
import dev.quwac.koyane.authentication.bearer.authenticated
import dev.quwac.koyane.authentication.bearer.principal

val users = BearerAuthentication<User>()
val app = koyane {
    install(users) { verify { User(1L) } }
    install(Http) {
        get("/", authenticated(users) {
            val user: String = principal(users)
            text(user)
        })
    }
}

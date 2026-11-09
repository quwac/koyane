package fixtures

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.authentication.bearer.BearerAuthentication

val users = BearerAuthentication<User>()
val app = koyane {
    install(users) { verify { "wrong principal type" } }
}

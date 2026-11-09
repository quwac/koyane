package fixtures

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http

val app = koyane {
    install(Http) {
        get("/inputs/{value}") {
            val value: String = param.byte("value")
            text(value)
        }
    }
}

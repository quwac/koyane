package fixtures

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http

val app = koyane {
    install(Http) {
        get("/inputs") {
            val value: String = query.parse<Int>("value", String::toIntOrNull)
            text(value)
        }
    }
}

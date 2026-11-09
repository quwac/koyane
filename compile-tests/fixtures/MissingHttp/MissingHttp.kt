package fixtures

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.text

val app = koyane {
    get("/users/{id}") { text("HTTP must be explicitly installed") }
}

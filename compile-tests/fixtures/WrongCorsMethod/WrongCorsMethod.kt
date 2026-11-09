package fixtures

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.cors.Cors

val app = koyane {
    install(Cors) { allowMethod("PATCH") }
}

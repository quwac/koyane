package fixtures

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.logging.Logging

val app = koyane {
    install(Logging) { sink = "not a function" }
}

package fixtures

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.errorhandling.ErrorHandling

val app = koyane {
    install(ErrorHandling) {
        exception<IllegalStateException> { "error handlers must return HttpResponse" }
    }
}

package fixtures

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpResponse

val app = koyane {
    install(Http) {
        exception<IllegalStateException> { HttpResponse(500) }
    }
}

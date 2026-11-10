package dev.quwac.koyane.example.lambdaweb

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpStatus

/** Builds the Node.js Lambda Web Functions example with its HTTP integration-test routes. */
fun createApplication(): Application = koyane {
    install(Http) {
        get("/hello/:name") { text("Hello, ${param.string("name")}!") }
        post("/echo") {
            text("${query.string("prefix")}:${header.string("X-Message")}:${body.text()}")
        }
        get("/headers") { text("${header.string("X-Value")}|${header.string("Cookie")}") }
        head("/head") { text("This body is omitted", status = HttpStatus.OK) }
        delete("/empty") { empty(HttpStatus.NO_CONTENT) }
        get("/failure") { error("private failure detail") }
    }
}

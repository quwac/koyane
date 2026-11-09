package dev.quwac.koyane.example.cloudrunfunctions

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.errorhandling.ErrorHandling
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpResponse
import dev.quwac.koyane.http.HttpStatus
import kotlinx.coroutines.delay

private const val RESPONSE_DELAY_MS = 10L

fun createApplication(): Application = koyane {
    install(ErrorHandling)
    install(Http) {
        get("/hello/:name") { text("Hello, ${param.string("name")}!") }
        post("/echo") {
            text("${query.string("prefix")}:${header.string("X-Message")}:${body.text()}")
        }
        get("/headers") { text("${header.string("X-Value")}|${header.string("Cookie")}") }
        head("/head") { text("Omitted body") }
        delete("/empty") { empty(HttpStatus.NO_CONTENT) }
        get("/cookie") {
            HttpResponse(
                HttpStatus.OK.code,
                "cookie",
                mapOf("Set-Cookie" to "session=test; Expires=Wed, 01 Jan 2031 00:00:00 GMT; HttpOnly"),
            )
        }
        get("/slow/:name") {
            delay(RESPONSE_DELAY_MS)
            text(param.string("name"))
        }
        get("/failure") { error("private failure detail") }
    }
}

fun main() = Unit

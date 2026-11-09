package dev.quwac.koyane.testing

import dev.quwac.koyane.core.ApplicationBuilder
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpResponse
import dev.quwac.koyane.http.HttpStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestApplicationTest {
    @Test
    fun configuresAnApplicationAndChecksAResponse() = testApplication {
        application {
            configureHelloApplication()
        }

        val response = client.get("/hello")

        assertEquals(200, response.status)
        assertEquals("hello", response.bodyAsText())
        assertEquals("handler", response.headers["X-Source"])
        assertEquals("handler", response.header("x-source"))
        assertNull(response.header("missing"))
    }

    @Test
    fun sendsBodiesHeadersPathsAndQueriesThroughHttp() = testApplication {
        application {
            install(Http) {
                post("/users/:id") {
                    text(
                        listOf(
                            param.int("id"),
                            query.string("term"),
                            header.string("Content-Type"),
                            header.string("X-Value"),
                            body.text(),
                        ).joinToString("|"),
                        HttpStatus.CREATED,
                    )
                }
            }
        }

        val response = client.post(
            "/users/42",
            headers = mapOf("Content-Type" to "text/plain"),
            body = "request body",
        ) {
            header("X-Value", "one")
            header("x-value", "two")
            query("term", "Kotlin / Wasm")
        }

        assertEquals(201, response.status)
        assertEquals("42|Kotlin / Wasm|text/plain|one, two|request body", response.bodyAsText())
    }

    @Test
    fun exposesFrameworkFailuresEmptyResponsesExplicitStatusesAndRedirects() = testApplication {
        application {
            install(Http) {
                get("/items/:id") { text(param.int("id").toString()) }
                get("/empty") { empty(HttpStatus.NO_CONTENT) }
                get("/created") { text("created", HttpStatus.CREATED) }
                get("/redirect") { redirect("/target") }
            }
        }

        assertEquals(400, client.get("/items/not-an-int").status)
        assertEquals(404, client.get("/missing").status)
        assertEquals("", client.get("/empty").bodyAsText())
        assertEquals(204, client.get("/empty").status)
        assertEquals(201, client.get("/created").status)
        val redirect = client.get("/redirect")
        assertEquals(302, redirect.status)
        assertEquals("/target", redirect.header("Location"))
    }

    @Test
    fun delegatesEverySupportedMethodToTheSameRequestPath() = testApplication {
        application {
            install(Http) {
                get("/method") { text(request.method) }
                post("/method") { text(request.method) }
                put("/method") { text(request.method) }
                patch("/method") { text(request.method) }
                delete("/method") { text(request.method) }
                head("/method") { empty(HttpStatus.NO_CONTENT) }
                options("/method") { empty(HttpStatus.NO_CONTENT) }
                query("/method") { text("${request.method}:${body.text()}") }
            }
        }

        assertEquals("GET", client.get("/method").bodyAsText())
        assertEquals("POST", client.post("/method").bodyAsText())
        assertEquals("PUT", client.put("/method").bodyAsText())
        assertEquals("PATCH", client.patch("/method").bodyAsText())
        assertEquals("DELETE", client.delete("/method").bodyAsText())
        assertEquals(204, client.head("/method").status)
        assertEquals(204, client.options("/method").status)
        assertEquals("QUERY:body", client.query("/method", body = "body").bodyAsText())
    }

    @Test
    fun isolatesApplicationConfigurationAndMutableState() = runTest {
        var firstCount = 0
        runTestApplication {
            application {
                install(Http) { get("/") { text((++firstCount).toString()) } }
            }
            assertEquals("1", client.get("/").bodyAsText())
        }

        var secondCount = 0
        runTestApplication {
            application {
                install(Http) { get("/") { text((++secondCount).toString()) } }
            }
            assertEquals("1", client.get("/").bodyAsText())
        }

        assertEquals(1, firstCount)
        assertEquals(1, secondCount)
    }
}

private fun ApplicationBuilder.configureHelloApplication() {
    install(Http) {
        get("/hello") {
            HttpResponse(200, "hello", mapOf("X-Source" to "handler"))
        }
    }
}

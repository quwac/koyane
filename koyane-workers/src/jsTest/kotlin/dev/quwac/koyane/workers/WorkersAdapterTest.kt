@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.workers

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.logging.Logging
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.js.unsafeCast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private external interface TestEnvironment : JsAny {
    val message: String
}

class WorkersAdapterTest {
    @Test
    fun handlesNativeFetchRequestsWithASharedApplication(): Promise<Unit> {
        val messages = mutableListOf<String>()
        val application = koyane {
            install(Logging) { sink = { messages += it } }
            install(Http) {
                get("/") { text("Hello") }
            }
        }
        val request = js("new Request('https://example.test/')").unsafeCast<WorkerRequest>()

        return WorkersAdapter(application).handle(request).then { response ->
            assertEquals(200, response.status)
            response.text()
        }.then { body ->
            assertEquals("Hello", body)
            assertEquals(listOf("Request GET /", "Response GET / 200"), messages)
        }
    }

    @Test
    fun definesOneWorkerAndReusesItsApplication(): Promise<Unit> {
        var applicationCount = 0
        defineWorker<TestEnvironment> { environment ->
            applicationCount += 1
            koyane {
                install(Http) {
                    get("/") { text(environment.message) }
                }
            }
        }
        assertFailsWith<IllegalStateException> {
            defineWorker<TestEnvironment> { error("must not replace the Worker") }
        }

        val environment = js("({ message: 'Hello from main' })").unsafeCast<TestEnvironment>()
        val firstRequest = js("new Request('https://example.test/')").unsafeCast<WorkerRequest>()
        return fetch(firstRequest, environment).then { response ->
            response.text()
        }.then { body ->
            assertEquals("Hello from main", body)
            val secondRequest = js("new Request('https://example.test/')").unsafeCast<WorkerRequest>()
            fetch(secondRequest, environment)
        }.then { response ->
            assertEquals(200, response.status)
            assertEquals(1, applicationCount)
        }
    }
}

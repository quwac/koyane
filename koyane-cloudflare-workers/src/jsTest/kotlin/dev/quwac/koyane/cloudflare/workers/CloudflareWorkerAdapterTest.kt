@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.cloudflare.workers

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

class CloudflareWorkerAdapterTest {
    @Test
    fun handlesNativeFetchRequestsWithASharedApplication(): Promise<Unit> {
        val messages = mutableListOf<String>()
        val application = koyane {
            install(Logging) { sink = { messages += it } }
            install(Http) {
                get("/") { text("Hello") }
            }
        }
        val request = js("new Request('https://example.test/')").unsafeCast<CloudflareWorkerRequest>()

        return CloudflareWorkerAdapter(application).handle(request).then { response ->
            assertEquals(200, response.status)
            response.text()
        }.then { body ->
            assertEquals("Hello", body)
            assertEquals(listOf("Request GET /", "Response GET / 200"), messages)
        }
    }

    @Test
    fun definesOneCloudflareWorkerAndReusesItsApplication(): Promise<Unit> {
        var applicationCount = 0
        defineCloudflareWorker<TestEnvironment> { environment ->
            applicationCount += 1
            koyane {
                install(Http) {
                    get("/") { text(environment.message) }
                }
            }
        }
        assertFailsWith<IllegalStateException> {
            defineCloudflareWorker<TestEnvironment> { error("must not replace the Cloudflare Worker") }
        }

        val environment = js("({ message: 'Hello from main' })").unsafeCast<TestEnvironment>()
        val firstRequest = js("new Request('https://example.test/')").unsafeCast<CloudflareWorkerRequest>()
        return fetch(firstRequest, environment).then { response ->
            response.text()
        }.then { body ->
            assertEquals("Hello from main", body)
            val secondRequest = js("new Request('https://example.test/')").unsafeCast<CloudflareWorkerRequest>()
            fetch(secondRequest, environment)
        }.then { response ->
            assertEquals(200, response.status)
            assertEquals(1, applicationCount)
        }
    }
}

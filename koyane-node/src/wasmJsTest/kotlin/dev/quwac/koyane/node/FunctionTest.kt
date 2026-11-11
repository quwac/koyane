@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.node

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.test.runTest
import kotlin.js.JsAny
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Verifies the common factory through the native host entry point. */
class FunctionTest {
    /** Each call reads its environment, owns resources, and releases them after response processing or failure. */
    @Test
    fun invokesCommonFactoryWithIsolatedResources() = runTest {
        val released = mutableListOf<String>()
        nodeFunction { env ->
            val job = checkNotNull(currentCoroutineContext()[Job])
            val value = resource({ env.getString("VALUE") }) { released += it }
            if (value == "factory") error("factory failure")
            koyane {
                install(Http) {
                    get("/") {
                        assertSame(job, currentCoroutineContext()[Job])
                        delay(1)
                        if (value == "cancel") {
                            currentCoroutineContext().cancel()
                            currentCoroutineContext().ensureActive()
                        }
                        assertTrue(value !in released)
                        if (value == "handler") error("handler failure")
                        text(value)
                    }
                }
            }
        }
        val responses = listOf("one", "two").map { value ->
            async { invoke(value) }
        }.awaitAll()
        assertEquals(listOf("one", "two"), responses)
        assertEquals(setOf("one", "two"), released.toSet())
        val value = "factory"
        assertFails { invoke(value) }
        assertEquals("Internal Server Error", invoke("handler"))
        assertFails { invoke("cancel") }
        assertEquals(setOf("one", "two", "factory", "handler", "cancel"), released.toSet())
        assertEquals(released.toSet().size, released.size)
        assertFails { nodeFunction { koyane { } } }
    }
}

@Suppress("UnusedParameter")
private fun environment(value: String): JsAny = js("({ VALUE: value })")
private suspend fun invoke(value: String): String {
    val response = response()
    handleNodeRequest(request(), response, "", environment(value)).await()
    return body(response)
}
private fun request(): NodeRequest = js("({ method: 'GET', url: '/', rawHeaders: [] })")
private fun response(): NodeResponse = js(
    """({
    statusCode: 0, destroyed: false, headersSent: false, headers: {}, body: '',
    setHeader(name, value) { this.headers[name] = value; },
    getHeaderNames() { return Object.keys(this.headers); },
    removeHeader(name) { delete this.headers[name]; },
    end(body) { this.body = body; },
    destroy() { this.destroyed = true; }
})""",
)

@Suppress("UnusedParameter")
private fun body(response: NodeResponse): String = js("response.body")

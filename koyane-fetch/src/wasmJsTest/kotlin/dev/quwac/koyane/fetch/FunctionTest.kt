@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.fetch

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
        fetchFunction { env ->
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
            async { handleFetchRequest(request(), environment(value)).await().text().await().toString() }
        }.awaitAll()
        assertEquals(listOf("one", "two"), responses)
        assertEquals(setOf("one", "two"), released.toSet())
        val value = "factory"
        assertFails { handleFetchRequest(request(), environment(value)).await().text().await().toString() }
        assertFails { handleFetchRequest(request(), environment("handler")).await().text().await().toString() }
        assertFails { handleFetchRequest(request(), environment("cancel")).await().text().await().toString() }
        assertEquals(setOf("one", "two", "factory", "handler", "cancel"), released.toSet())
        assertEquals(released.toSet().size, released.size)
        assertFails { fetchFunction { koyane { } } }
    }
}

@Suppress("UnusedParameter")
private fun environment(value: String): JsAny = js("({ VALUE: value })")
private fun request(): FetchRequest = js("new Request('https://example.test/')")

package dev.quwac.koyane.node

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NodeServerTest {
    @Test
    fun listensOnAnAvailablePortAndCloses() = runTest {
        val application = koyane { install(Http) }
        val server = application.listen(port = 0)
        assertTrue(server.port > 0)
        server.close()
    }

    @Test
    fun reportsBindFailures() = runTest {
        val application = koyane { install(Http) }
        val first = application.listen(port = 0)
        try {
            assertFailsWith<IllegalStateException> { application.listen(port = first.port) }
        } finally {
            first.close()
        }
    }

    @Test
    fun rejectsMissingHttpAndInvalidListenAddresses() = runTest {
        assertFailsWith<IllegalStateException> { koyane {}.listen(port = 0) }
        val application = koyane { install(Http) }
        assertFailsWith<IllegalArgumentException> { application.listen(host = "") }
        assertFailsWith<IllegalArgumentException> { application.listen(port = -1) }
    }

    @Test
    fun retainsFactoryResourcesUntilTheServerCloses() = runTest {
        val released = mutableListOf<String>()
        var factories = 0
        val server = nodeServer(port = 0) { environment ->
            factories++
            assertTrue(environment.getString("PATH").isNotBlank())
            resource({ "database" }) { released += it }
            resource({ "http" }) { released += it }
            koyane { install(Http) }
        }
        try {
            assertEquals(1, factories)
            assertEquals(emptyList(), released)
        } finally {
            server.close()
        }
        assertEquals(listOf("http", "database"), released)
    }

    @Test
    fun releasesResourcesAndPreservesFactoryFailures() = runTest {
        val failure = IllegalStateException("factory failure")
        var released = false
        val actual = assertFailsWith<IllegalStateException> {
            nodeServer(port = 0) {
                resource({ "client" }) { released = true }
                throw failure
            }
        }
        assertSame(failure, actual)
        assertTrue(released)
    }

    @Test
    fun releasesResourcesWhenBindingThePortFails() = runTest {
        val first = koyane { install(Http) }.listen(port = 0)
        var released = false
        try {
            assertFailsWith<IllegalStateException> {
                nodeServer(port = first.port) {
                    resource({ "client" }) { released = true }
                    koyane { install(Http) }
                }
            }
            assertTrue(released)
        } finally {
            first.close()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun finishesActiveRequestsBeforeReleasingResourcesEvenWhenCloseIsCancelled() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var released = false
        val server = nodeServer(port = 0) {
            resource({ "client" }) { released = true }
            koyane {
                install(Http) {
                    get("/") {
                        started.complete(Unit)
                        finish.await()
                        assertFalse(released)
                        text("done")
                    }
                }
            }
        }
        var shutdown: Job? = null
        try {
            val response = async { readServer("http://127.0.0.1:${server.port}/") }
            started.await()
            shutdown = launch {
                currentCoroutineContext().cancel()
                server.close()
            }
            runCurrent()
            assertFalse(shutdown.isCompleted)
            assertFalse(released)
            finish.complete(Unit)
            assertEquals("done", response.await())
            shutdown.join()
            assertTrue(released)
        } finally {
            finish.complete(Unit)
            shutdown?.join() ?: server.close()
        }
    }
}

internal expect suspend fun readServer(url: String): String

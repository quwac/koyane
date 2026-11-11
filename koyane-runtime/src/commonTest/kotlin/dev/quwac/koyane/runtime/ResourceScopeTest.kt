package dev.quwac.koyane.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class ResourceScopeTest {
    @Test
    fun releasesResourcesInReverseOrderAfterSuccess() = runTest {
        val events = mutableListOf<String>()
        val result = withResourceScope {
            resource({ "database" }) { events += it }
            resource({ "http" }) { events += it }
            events += "response"
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf("response", "http", "database"), events)
    }

    @Test
    fun preservesRequestFailureAndReleasesEveryResource() = runTest {
        val requestFailure = IllegalStateException("request")
        val releaseFailure = IllegalArgumentException("release")
        val released = mutableListOf<String>()
        val actual = assertFailsWith<IllegalStateException> {
            withResourceScope {
                resource({ "database" }) { released += it }
                resource({ "http" }) {
                    released += it
                    throw releaseFailure
                }
                throw requestFailure
            }
        }
        assertSame(requestFailure, actual)
        assertEquals(listOf(releaseFailure), actual.suppressedExceptions)
        assertEquals(listOf("http", "database"), released)
    }

    @Test
    fun releasesPreviousResourcesWhenAcquisitionFails() = runTest {
        val released = mutableListOf<String>()
        assertFailsWith<IllegalArgumentException> {
            withResourceScope {
                resource({ "database" }) { released += it }
                resource<String>({ throw IllegalArgumentException("http") }) { released += it }
            }
        }
        assertEquals(listOf("database"), released)
    }

    @Test
    fun releasesResourcesWhenOperationIsCancelled() = runTest {
        var released = false
        launch {
            withResourceScope {
                resource({ "database" }) {
                    currentCoroutineContext().ensureActive()
                    released = true
                }
                currentCoroutineContext().cancel()
                currentCoroutineContext().ensureActive()
            }
        }.join()
        assertEquals(true, released)
    }

    @Test
    fun reportsReleaseFailureAfterReleasingRemainingResources() = runTest {
        val failure = IllegalArgumentException("release")
        var databaseClosed = false
        val actual = assertFailsWith<IllegalArgumentException> {
            withResourceScope {
                resource({ "database" }) { databaseClosed = true }
                resource({ "http" }) { throw failure }
            }
        }
        assertSame(failure, actual)
        assertEquals(true, databaseClosed)
    }

    @Test
    fun doesNotAcquireResourcesAfterScopeCloses() = runTest {
        val scope = withResourceScope { this }
        assertFailsWith<IllegalStateException> {
            scope.resource({ error("must not acquire") }) { }
        }
    }

    @Test
    fun waitsForPendingAcquisitionAndReleasesItBeforeCloseReturns() = runTest {
        val scope = ResourceScope()
        val finish = CompletableDeferred<Unit>()
        val released = mutableListOf<String>()
        scope.resource({ "first" }) { released += it }
        val acquisition = async(start = CoroutineStart.UNDISPATCHED) {
            assertFailsWith<IllegalStateException> {
                scope.resource({
                    finish.await()
                    "pending"
                }) { released += it }
            }
        }
        val closing = async(start = CoroutineStart.UNDISPATCHED) { scope.close() }
        assertFalse(closing.isCompleted)
        finish.complete(Unit)
        acquisition.await()
        closing.await()
        assertEquals(listOf("pending", "first"), released)
        scope.close()
        assertEquals(listOf("pending", "first"), released)
    }

    @Test
    fun concurrentCloseWaitsForTheSameReleaseAndReportsItsFailure() = runTest {
        val scope = ResourceScope()
        val finish = CompletableDeferred<Unit>()
        val failure = IllegalArgumentException("release")
        var releases = 0
        scope.resource({ "client" }) {
            releases++
            finish.await()
            throw failure
        }
        val first = async(start = CoroutineStart.UNDISPATCHED) { runCatching { scope.close() } }
        val second = async(start = CoroutineStart.UNDISPATCHED) { runCatching { scope.close() } }
        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)
        assertEquals(1, releases)
        finish.complete(Unit)
        assertSame(failure, first.await().exceptionOrNull())
        assertSame(failure, second.await().exceptionOrNull())
        assertEquals(1, releases)
    }

    @Test
    fun failedAcquisitionDoesNotPreventCloseFromFinishing() = runTest {
        val scope = ResourceScope()
        val finish = CompletableDeferred<Unit>()
        val failure = IllegalArgumentException("acquire")
        var releases = 0
        scope.resource({ "first" }) { releases++ }
        val acquisition = async(start = CoroutineStart.UNDISPATCHED) {
            val actual = assertFailsWith<IllegalArgumentException> {
                scope.resource<String>({
                    finish.await()
                    throw failure
                }) { error("not acquired") }
            }
            assertSame(failure, actual)
        }
        val closing = async(start = CoroutineStart.UNDISPATCHED) { scope.close() }
        finish.complete(Unit)
        acquisition.await()
        closing.await()
        assertEquals(1, releases)
    }

    @Test
    fun cancelledAcquisitionDoesNotPreventCloseFromFinishing() = runTest {
        val scope = ResourceScope()
        val finish = CompletableDeferred<Unit>()
        var releases = 0
        scope.resource({ "first" }) { releases++ }
        val acquisition = launch(start = CoroutineStart.UNDISPATCHED) {
            scope.resource({ finish.await() }) { error("not acquired") }
        }
        val closing = async(start = CoroutineStart.UNDISPATCHED) { scope.close() }
        acquisition.cancel()
        acquisition.join()
        closing.await()
        assertEquals(1, releases)
    }
}

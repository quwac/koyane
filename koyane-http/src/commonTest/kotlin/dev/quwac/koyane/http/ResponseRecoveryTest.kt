package dev.quwac.koyane.http

import dev.quwac.koyane.core.koyane
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class ResponseRecoveryTest {
    @Test
    fun excludesEachFailedTransformOnceAndPreservesFreshResponseBodies() {
        var firstFailures = 0
        var secondFailures = 0
        var recoveries = 0
        var ready = 0
        val app = koyane {
            install(
                Hooks {
                    onResponse { it.copy(body = it.body + "!") }
                    onResponse {
                        firstFailures++
                        error("first")
                    }
                    onResponse {
                        secondFailures++
                        error("second")
                    }
                    onResponse { it.copy(headers = it.headers + ("X-Final" to "yes")) }
                    onError {
                        recoveries++
                        HttpResponse(500, "error")
                    }
                    onResponseReady { ready++ }
                },
            )
            install(Http) { get("/") { text("ok") } }
        }
        val response = runImmediately { app.fetch(HttpRequest("GET", "/")) }
        assertEquals(HttpResponse(500, "error!", mapOf("X-Final" to "yes")), response)
        assertEquals(1, firstFailures)
        assertEquals(1, secondFailures)
        assertEquals(2, recoveries)
        assertEquals(1, ready)
    }

    @Test
    fun excludesATransformThatOnlyFailsOnTheRecoveredResponse() {
        val first = IllegalStateException("recovered response rejected")
        val second = IllegalArgumentException("initial response rejected")
        var attempts = 0
        val app = koyane {
            install(
                Hooks {
                    onResponse {
                        attempts++
                        if (it.status != HttpStatus.OK.code) throw first
                        it
                    }
                    onResponse { throw second }
                    onResponse { it.copy(headers = mapOf("X-Final" to "yes")) }
                    onError { if (it === second) HttpResponse(500) else HttpResponse(503) }
                },
            )
            install(Http) { get("/") { text("ok") } }
        }
        assertEquals(
            HttpResponse(503, headers = mapOf("X-Final" to "yes")),
            runImmediately {
                app.fetch(HttpRequest("GET", "/"))
            },
        )
        assertEquals(2, attempts)
    }

    @Test
    fun observerFailuresPropagateWithoutReprocessingTheResponse() {
        val failure = IllegalStateException("observer failed")
        var transforms = 0
        var observations = 0
        val app = koyane {
            install(
                Hooks {
                    onResponse {
                        transforms++
                        it
                    }
                    onResponseReady {
                        observations++
                        throw failure
                    }
                    onError { error("observer failure must not restart processing") }
                },
            )
            install(Http) { get("/") { text("ok") } }
        }
        assertSame(
            failure,
            assertFailsWith<IllegalStateException> {
                runImmediately { app.fetch(HttpRequest("GET", "/")) }
            },
        )
        assertEquals(1, transforms)
        assertEquals(1, observations)
    }

    @Test
    fun unhandledFailuresAndCancellationDoNotNotifyResponseObservers() {
        for (failure in listOf(IllegalStateException("failed"), CancellationException("cancelled"))) {
            val app = koyane {
                install(
                    Hooks {
                        onResponse { throw failure }
                        onResponseReady { error("there is no final response") }
                        onError { null }
                    },
                )
                install(Http) { get("/") { text("ok") } }
            }
            assertSame(
                failure,
                assertFailsWith<Exception> {
                    runImmediately { app.fetch(HttpRequest("GET", "/")) }
                },
            )
        }
    }

    @Test
    fun suspendsRecoveryAndObservationAfterAResponseTransformFails() {
        lateinit var pending: Continuation<Unit>
        var observed: HttpResponse? = null
        val app = koyane {
            install(
                Hooks {
                    onResponse { error("failed") }
                    onResponse { it.copy(headers = mapOf("X-Recovered" to "yes")) }
                    onError {
                        suspendCoroutine { pending = it }
                        HttpResponse(500)
                    }
                    onResponseReady {
                        observed = it
                        suspendCoroutine { continuation -> pending = continuation }
                    }
                },
            )
            install(Http) { get("/") { text("ok") } }
        }
        var completed: Result<HttpResponse>? = null
        val operation: suspend () -> HttpResponse = { app.fetch(HttpRequest("GET", "/")) }
        operation.startCoroutine(object : Continuation<HttpResponse> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<HttpResponse>) {
                completed = result
            }
        })
        assertNull(observed)
        assertNull(completed)
        pending.resume(Unit)
        assertEquals(HttpResponse(500, headers = mapOf("X-Recovered" to "yes")), observed)
        assertNull(completed)
        pending.resume(Unit)
        assertSame(observed, completed!!.getOrThrow())
    }
}

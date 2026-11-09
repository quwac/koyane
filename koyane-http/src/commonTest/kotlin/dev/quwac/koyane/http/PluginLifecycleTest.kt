package dev.quwac.koyane.http

import dev.quwac.koyane.core.AttributeKey
import dev.quwac.koyane.core.Plugin
import dev.quwac.koyane.core.PluginScope
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

class PluginLifecycleTest {
    @Test
    fun runsHooksInRegistrationOrderAndSharesTypedState() {
        val events = mutableListOf<String>()
        val applicationKey = AttributeKey<String>("application")
        val callKey = AttributeKey<String>("call")
        lateinit var received: HttpCall
        val first = Hooks {
            onRequest {
                received = this
                attributes[callKey] = application.attributes[applicationKey]
                events += "request 1"
                null
            }
            beforeHandler {
                assertEquals("42", param.string("id"))
                events += "before 1"
                null
            }
            onResponse { response ->
                assertSame(received, this)
                events += "response 1"
                response.copy(headers = response.headers + ("X-Plugin" to attributes[callKey]))
            }
        }
        val second = Hooks {
            onRequest {
                assertEquals("shared", attributes[callKey])
                events += "request 2"
                null
            }
            beforeHandler {
                events += "before 2"
                null
            }
            onResponse { response ->
                assertEquals("shared", response.headers["X-Plugin"])
                events += "response 2"
                response.copy(body = response.body + "!")
            }
        }
        val app = koyane {
            attributes[applicationKey] = "shared"
            install(first)
            install(Http) {
                get("/users/{id}") {
                    assertSame(received, call)
                    assertEquals("shared", attributes[callKey])
                    events += "handler"
                    text(param.string("id"))
                }
            }
            install(second)
        }
        val response = runImmediately { app.fetch(HttpRequest("GET", "/users/42")) }
        assertEquals("42!", response.body)
        assertEquals(
            listOf("request 1", "request 2", "before 1", "before 2", "handler", "response 1", "response 2"),
            events,
        )
    }

    @Test
    fun shortCircuitsBeforeRoutingOrBeforeTheHandler() {
        for (stopAt in listOf("request", "handler")) {
            val events = mutableListOf<String>()
            val app = koyane {
                install(
                    Hooks {
                        onRequest {
                            events += "request"
                            if (stopAt == "request") HttpResponse(403) else null
                        }
                        beforeHandler {
                            events += "before ${param.int("id")}"
                            HttpResponse(401)
                        }
                        onResponse { response ->
                            events += "response"
                            response
                        }
                    },
                )
                install(
                    Hooks {
                        onRequest {
                            events += "later request"
                            null
                        }
                        beforeHandler { error("must be skipped") }
                    },
                )
                install(Http) { get("/{id}") { error("must be skipped") } }
            }
            val response = runImmediately { app.fetch(HttpRequest("GET", "/7")) }
            assertEquals(if (stopAt == "request") 403 else 401, response.status)
            assertEquals(
                if (stopAt == "request") {
                    listOf("request", "response")
                } else {
                    listOf("request", "later request", "before 7", "response")
                },
                events,
            )
        }
    }

    @Test
    fun sendsFrameworkFailuresThroughErrorHooksBeforeTransformingTheirResponses() {
        val events = mutableListOf<String>()
        val app = koyane {
            install(
                Hooks {
                    onRequest {
                        events += "request"
                        null
                    }
                    beforeHandler {
                        events += "before"
                        null
                    }
                    onResponse { response ->
                        events += "response ${response.status}"
                        response.copy(headers = response.headers + ("X-Plugin" to "yes"))
                    }
                    onError { cause ->
                        events += "error ${(cause as HttpFailure).response.status}"
                        null
                    }
                },
            )
            install(Http) { get("/items/{id}") { text(param.int("id").toString()) } }
        }
        for ((request, status) in listOf(
            HttpRequest("GET", "/%GG") to 400,
            HttpRequest("GET", "/missing") to 404,
            HttpRequest("POST", "/items/1") to 405,
            HttpRequest("GET", "/items/no") to 400,
        )) {
            events.clear()
            val response = runImmediately { app[Http].handle(request) }
            assertEquals(status, response.status)
            assertEquals("yes", response.headers["X-Plugin"])
            assertEquals("request", events.first())
            assertEquals("response $status", events.last())
            assertEquals(if (request.url == "/items/no") 4 else 3, events.size)
            assertEquals("error $status", events[events.lastIndex - 1])
        }
    }

    @Test
    fun recoversFailuresFromEveryLifecycleStageWithoutRetryingFailedResponseHooks() {
        for (stage in listOf("request", "before", "handler", "response")) {
            val failure = IllegalStateException(stage)
            fun failAt(candidate: String) {
                if (stage == candidate) throw failure
            }
            val events = mutableListOf<String>()
            val app = koyane {
                install(
                    Hooks {
                        onRequest {
                            failAt("request")
                            null
                        }
                        beforeHandler {
                            failAt("before")
                            null
                        }
                        onResponse { response ->
                            events += "response ${response.status}"
                            failAt("response")
                            response
                        }
                        onError { cause ->
                            assertSame(failure, cause)
                            events += "observe"
                            null
                        }
                    },
                )
                install(
                    Hooks {
                        onError {
                            events += "recover"
                            empty(HttpStatus.INTERNAL_SERVER_ERROR)
                        }
                    },
                )
                install(Hooks { onError { error("first recovery ends error handling") } })
                install(Http) {
                    get("/") {
                        failAt("handler")
                        text("ok")
                    }
                }
            }
            assertEquals(500, runImmediately { app.fetch(HttpRequest("GET", "/")) }.status)
            assertEquals(
                if (stage == "response") {
                    listOf("response 200", "observe", "recover")
                } else {
                    listOf("observe", "recover", "response 500")
                },
                events,
            )
        }
    }

    @Test
    fun preservesUnhandledFailuresAndDoesNotRecursivelyHandleErrorHookFailures() {
        val original = IllegalStateException("original")
        val hookFailure = IllegalArgumentException("hook failed")
        for (errorHookFails in listOf(false, true)) {
            var calls = 0
            val app = koyane {
                install(
                    Hooks {
                        onError { cause ->
                            assertSame(original, cause)
                            calls++
                            if (errorHookFails) throw hookFailure
                            null
                        }
                    },
                )
                install(Http) { get("/") { throw original } }
            }
            val thrown = assertFailsWith<Exception> { runImmediately { app.fetch(HttpRequest("GET", "/")) } }
            assertSame(if (errorHookFails) hookFailure else original, thrown)
            assertEquals(1, calls)
        }
    }

    @Test
    fun propagatesCancellationWithoutRecovery() {
        val cancellation = CancellationException("cancelled")
        val app = koyane {
            install(Hooks { onError { error("cancellation must propagate") } })
            install(Http) { get("/") { throw cancellation } }
        }
        assertSame(
            cancellation,
            assertFailsWith<CancellationException> {
                runImmediately { app.fetch(HttpRequest("GET", "/")) }
            },
        )
    }

    @Test
    fun isolatesAttributesAcrossInterleavedSuspendedCalls() {
        val key = AttributeKey<String>("request id")
        val pending = mutableListOf<Continuation<Unit>>()
        val responses = mutableListOf<String>()
        val app = koyane {
            install(
                Hooks {
                    onRequest {
                        assertNull(attributes.getOrNull(key))
                        attributes[key] = request.url
                        suspendCoroutine { pending += it }
                        null
                    }
                    beforeHandler {
                        assertEquals(request.url, attributes[key])
                        suspendCoroutine { pending += it }
                        null
                    }
                    onResponse { response ->
                        assertEquals(request.url, attributes[key])
                        response
                    }
                },
            )
            install(Http) { get("/{id}") { text(attributes[key]) } }
        }
        fun start(path: String) {
            val operation: suspend () -> HttpResponse = { app.fetch(HttpRequest("GET", path)) }
            operation.startCoroutine(object : Continuation<HttpResponse> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<HttpResponse>) {
                    responses += result.getOrThrow().body
                }
            })
        }

        start("/one")
        start("/two")
        assertEquals(2, pending.size)
        assertEquals(emptyList(), responses)
        pending[1].resume(Unit)
        pending[0].resume(Unit)
        pending[2].resume(Unit)
        pending[3].resume(Unit)
        assertEquals(listOf("/two", "/one"), responses)
        start("/three")
        pending[4].resume(Unit)
        pending[5].resume(Unit)
        assertEquals(listOf("/two", "/one", "/three"), responses)
        assertNull(app.attributes.getOrNull(key))
    }

    @Test
    fun suspendsErrorAndResponseHooksWithTheSameCallState() {
        val key = AttributeKey<String>("state")
        lateinit var pending: Continuation<Unit>
        val events = mutableListOf<String>()
        val app = koyane {
            install(
                Hooks {
                    onError {
                        attributes[key] = "recovered"
                        events += "error"
                        suspendCoroutine { pending = it }
                        empty(HttpStatus.INTERNAL_SERVER_ERROR)
                    }
                    onResponse { response ->
                        events += attributes[key]
                        suspendCoroutine { pending = it }
                        response
                    }
                },
            )
            install(Http) { get("/") { error("failed") } }
        }
        var completed: Result<HttpResponse>? = null
        val operation: suspend () -> HttpResponse = { app.fetch(HttpRequest("GET", "/")) }
        operation.startCoroutine(object : Continuation<HttpResponse> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<HttpResponse>) {
                completed = result
            }
        })
        assertNull(completed)
        assertEquals(listOf("error"), events)
        pending.resume(Unit)
        assertNull(completed)
        assertEquals(listOf("error", "recovered"), events)
        pending.resume(Unit)
        assertEquals(500, completed!!.getOrThrow().status)
    }

    @Test
    fun rejectsRegistrationThroughAnExpiredScope() {
        lateinit var captured: PluginScope
        koyane { install(Hooks { captured = this }) }
        assertFailsWith<IllegalStateException> { captured.onRequest { null } }
        assertFailsWith<IllegalStateException> { captured.beforeHandler { null } }
        assertFailsWith<IllegalStateException> { captured.onResponse { it } }
        assertFailsWith<IllegalStateException> { captured.onResponseReady {} }
        assertFailsWith<IllegalStateException> { captured.onError { null } }
    }
}

internal class Hooks(private val configure: PluginScope.() -> Unit) : Plugin<Unit, Unit> {
    override fun createConfiguration() = Unit
    override fun install(configuration: Unit) = Unit
    override fun onInstall(scope: PluginScope, extension: Unit) = configure(scope)
}

internal fun <T> runImmediately(block: suspend () -> T): T {
    var completed: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            completed = result
        }
    })
    return checkNotNull(completed) { "This test expects immediate completion" }.getOrThrow()
}

package dev.quwac.koyane.authentication.bearer

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.cors.Cors
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpRequest
import dev.quwac.koyane.http.HttpResponse
import dev.quwac.koyane.http.HttpStatus
import dev.quwac.koyane.http.beforeHandler
import dev.quwac.koyane.http.fetch
import dev.quwac.koyane.http.onError
import dev.quwac.koyane.http.onRequest
import dev.quwac.koyane.http.onResponse
import dev.quwac.koyane.http.onResponseReady
import dev.quwac.koyane.logging.Logging
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

class BearerLifecycleTest {
    @Test
    fun authenticatesAfterBeforeHandlerHooksAndSharesThePrincipalWithResponseHooks() {
        val users = BearerAuthentication<String>()
        val events = mutableListOf<String>()
        val app = koyane {
            install(
                Hooks {
                    onRequest {
                        assertNull(principalOrNull(users))
                        events += "request"
                        null
                    }
                    beforeHandler {
                        assertNull(principalOrNull(users))
                        events += "before"
                        null
                    }
                    onResponse { response ->
                        events += "response ${principalOrNull(users)}"
                        response
                    }
                    onResponseReady { events += "ready ${principalOrNull(users)}" }
                },
            )
            install(users) {
                verify {
                    events += "verify $it"
                    "Ada"
                }
            }
            install(Http) {
                get(
                    "/",
                    authenticated(users) { user ->
                        assertSame(user, principal(users))
                        events += "handler $user"
                        text("ok")
                    },
                )
            }
        }
        assertEquals(200, app.bearerFetch(mapOf("Authorization" to "Bearer demo")).status)
        assertEquals(listOf("request", "before", "verify demo", "handler Ada", "response Ada", "ready Ada"), events)
    }

    @Test
    fun leavesPublicFrameworkAndEarlyResponsesOutsideAuthentication() {
        var invocations = 0
        val users = BearerAuthentication<String>()
        val app = koyane {
            install(users) {
                verify {
                    invocations++
                    it
                }
            }
            install(Hooks { onRequest { if (request.url == "/early") HttpResponse(403) else null } })
            install(Http) {
                get("/private", authenticated(users) { error("must not run") })
                get("/public") {
                    assertNull(principalOrNull(users))
                    text("public")
                }
                options("/private") { empty(HttpStatus.NO_CONTENT) }
            }
        }
        for ((request, status) in listOf(
            HttpRequest("GET", "/missing") to 404,
            HttpRequest("POST", "/private") to 405,
            HttpRequest("GET", "/%GG") to 400,
            HttpRequest("GET", "/early") to 403,
            HttpRequest("GET", "/public", mapOf("Authorization" to "Bearer demo")) to 200,
            HttpRequest("OPTIONS", "/private") to 204,
        )) {
            val response = runImmediately { app.fetch(request) }
            assertEquals(status, response.status)
            assertNull(response.headers["WWW-Authenticate"])
        }
        assertEquals(0, invocations)
    }

    @Test
    fun leavesBeforeHandlerShortCircuitsOutsideAuthentication() {
        val users = BearerAuthentication<String>()
        val app = koyane {
            install(users) { verify { error("must not verify") } }
            install(Hooks { beforeHandler { HttpResponse(403) } })
            install(Http) { get("/", authenticated(users) { error("must not run") }) }
        }
        assertEquals(403, app.bearerFetch(mapOf("Authorization" to "Bearer demo")).status)
    }

    @Test
    fun combinesChallengesWithCorsAndFinalResponseLoggingInEitherInstallationOrder() {
        for (corsFirst in listOf(false, true)) {
            val messages = mutableListOf<String>()
            val users = BearerAuthentication<String>()
            val app = koyane {
                install(Logging) { sink = { messages += it } }
                if (corsFirst) {
                    install(Cors) {
                        allowOrigin(CLIENT_ORIGIN)
                        allowHeader("Authorization")
                    }
                }
                install(users) { verify { null } }
                install(Http) { get("/", authenticated(users) { error("must not run") }) }
                if (!corsFirst) {
                    install(Cors) {
                        allowOrigin(CLIENT_ORIGIN)
                        allowHeader("Authorization")
                    }
                }
                install(Hooks { onError { error("authentication rejection is not an exception") } })
            }
            for (credentials in listOf(null, "Bearer rejected", "Bearer bad,header")) {
                messages.clear()
                val headers = mapOf("Origin" to CLIENT_ORIGIN) +
                    (credentials?.let { mapOf("Authorization" to it) } ?: emptyMap())
                val response = app.bearerFetch(headers)
                val expected = if (credentials == "Bearer bad,header") 400 else 401
                assertEquals(expected, response.status)
                assertEquals(CLIENT_ORIGIN, response.headers["Access-Control-Allow-Origin"])
                assertEquals(listOf("Request GET /", "Response GET / $expected"), messages)
            }
            val preflight = HttpRequest(
                "OPTIONS",
                "/",
                mapOf(
                    "Origin" to CLIENT_ORIGIN,
                    "Access-Control-Request-Method" to "GET",
                    "Access-Control-Request-Headers" to "Authorization",
                ),
            )
            val response = runImmediately { app.fetch(preflight) }
            assertEquals(204, response.status)
            assertNull(response.headers["WWW-Authenticate"])
        }
    }

    @Test
    fun propagatesVerifierErrorsAndCancellationWithoutChangingThemIntoRejections() {
        val users = BearerAuthentication<String>()
        val failure = IllegalStateException("verifier unavailable")
        val stop = CancellationException("stop")
        var errorHooks = 0
        val app = koyane {
            install(users) { verify { throw (if (it == "stop") stop else failure) } }
            install(Http) { get("/", authenticated(users) { error("must not run") }) }
            install(
                Hooks {
                    onError { cause ->
                        errorHooks++
                        assertSame(failure, cause)
                        assertNull(principalOrNull(users))
                        HttpResponse(503)
                    }
                },
            )
        }
        assertEquals(503, app.bearerFetch(mapOf("Authorization" to "Bearer demo")).status)
        assertSame(
            stop,
            assertFailsWith<CancellationException> {
                app.bearerFetch(mapOf("Authorization" to "Bearer stop"))
            },
        )
        assertEquals(1, errorHooks)
        val unhandled = bearerApplication { throw failure }
        assertSame(
            failure,
            assertFailsWith<IllegalStateException> {
                unhandled.bearerFetch(mapOf("Authorization" to "Bearer demo"))
            },
        )
    }

    @Test
    fun retainsThePrincipalForHandlerAndResponseErrorRecoveryWithoutReverification() {
        for (responseFailure in listOf(false, true)) {
            val users = BearerAuthentication<String>()
            var invocations = 0
            val app = koyane {
                install(users) {
                    verify {
                        invocations++
                        "Ada"
                    }
                }
                install(
                    Http,
                ) { get("/", authenticated(users) { if (responseFailure) text("ok") else error("handler") }) }
                install(
                    Hooks {
                        if (responseFailure) onResponse { error("response") }
                        onError {
                            assertEquals("Ada", principalOrNull(users))
                            HttpResponse(500)
                        }
                        onResponseReady { assertEquals("Ada", principalOrNull(users)) }
                    },
                )
            }
            assertEquals(500, app.bearerFetch(mapOf("Authorization" to "Bearer demo")).status)
            assertEquals(1, invocations)
        }
    }

    @Test
    fun isolatesPrincipalsAcrossInterleavedSuspendedRequestsAndLaterCalls() {
        val users = BearerAuthentication<String>()
        val pending = mutableMapOf<String, Continuation<Unit>>()
        val responses = mutableListOf<String>()
        val app = koyane {
            install(users) {
                verify { token ->
                    suspendCoroutine { pending[token] = it }
                    token
                }
            }
            install(
                Hooks {
                    onRequest {
                        assertNull(principalOrNull(users))
                        null
                    }
                    onResponseReady { response -> assertEquals(response.body.ifEmpty { null }, principalOrNull(users)) }
                },
            )
            install(Http) { get("/", authenticated(users) { user -> text(user) }) }
        }
        fun start(token: String) {
            val operation: suspend () -> HttpResponse = {
                app.fetch(
                    HttpRequest("GET", "/", mapOf("Authorization" to "Bearer $token")),
                )
            }
            operation.startCoroutine(object : Continuation<HttpResponse> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<HttpResponse>) {
                    responses += result.getOrThrow().body
                }
            })
        }
        start("one")
        start("two")
        assertEquals(emptyList(), responses)
        pending.getValue("two").resume(Unit)
        pending.getValue("one").resume(Unit)
        assertEquals(listOf("two", "one"), responses)
        assertEquals(401, app.bearerFetch().status)
        start("three")
        pending.getValue("three").resume(Unit)
        assertEquals(listOf("two", "one", "three"), responses)
    }

    @Test
    fun usesTheOwningApplicationsVerifierForSharedRoutesAndPluginInstances() {
        val users = BearerAuthentication<String>()
        val handler = authenticated(users) { user -> text(user) }
        fun create(name: String, httpFirst: Boolean) = koyane {
            if (httpFirst) {
                install(Http) { get("/", handler) }
            }
            install(users) { verify { name } }
            if (!httpFirst) {
                install(Http) { get("/", handler) }
            }
        }
        val first = create("first", httpFirst = true)
        val second = create("second", httpFirst = false)
        for (app in listOf(first, second, first)) {
            assertEquals(
                if (app === second) "second" else "first",
                app.bearerFetch(mapOf("Authorization" to "Bearer demo")).body,
            )
        }
    }
}

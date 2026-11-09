package dev.quwac.koyane.authentication.bearer

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpRequest
import dev.quwac.koyane.http.HttpResponse
import dev.quwac.koyane.http.fetch
import dev.quwac.koyane.http.onResponseReady
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BearerAuthenticationTest {
    @Test
    fun challengesMissingAndUnsupportedCredentialsWithoutInvokingTheVerifier() {
        val app = bearerApplication { error("must not verify") }
        for (headers in listOf(emptyMap(), mapOf("Authorization" to "Basic synthetic"))) {
            val response = app.bearerFetch(headers)
            assertEquals(401, response.status)
            assertEquals("", response.body)
            assertEquals("Bearer realm=\"koyane\"", response.headers["WWW-Authenticate"])
        }
    }

    @Test
    fun acceptsCaseInsensitiveNamesAndSchemesAndPreservesOpaqueTokenValues() {
        val seen = mutableListOf<String>()
        val app = bearerApplication {
            seen += it
            it
        }
        for (token in listOf("demo", "aZ09-._~+/", "demo=", "demo===")) {
            val response = app.bearerFetch(mapOf("aUtHoRiZaTiOn" to "\t bEaReR   $token \t"))
            assertEquals(200, response.status)
            assertEquals(token, response.body)
            assertNull(response.headers["WWW-Authenticate"])
        }
        assertEquals(listOf("demo", "aZ09-._~+/", "demo=", "demo==="), seen)
    }

    @Test
    fun rejectsMalformedBearerCredentialsBeforeVerification() {
        val app = bearerApplication { error("must not verify") }
        for (value in listOf(
            "", "\t", "Bearer", "Bearer ", "Bearer\tdemo", "Bearer demo extra",
            "Bearer demo,other", "Bearer demo, Bearer other", "Bearer =", "Bearer =demo",
            "Bearer de=mo", "Bearer demo\r\n", "Bearer demo\u0000", "Bearer démo",
            "Bearer demo\u007f", "Bearer demo\u00a0", "Bearer \"demo\"",
        )) {
            val response = app.bearerFetch(mapOf("Authorization" to value))
            assertEquals(400, response.status, value)
            assertEquals("Bearer realm=\"koyane\", error=\"invalid_request\"", response.headers["WWW-Authenticate"])
            assertEquals("", response.body)
        }
    }

    @Test
    fun rejectsAmbiguousCaseVariantAuthorizationHeaders() {
        val app = bearerApplication { error("must not verify") }
        for (headers in listOf(
            mapOf("Authorization" to "Bearer demo", "authorization" to "Bearer demo"),
            mapOf("AUTHORIZATION" to "Basic synthetic", "Authorization" to "Bearer demo"),
        )) {
            assertEquals(400, app.bearerFetch(headers).status)
        }
        assertEquals(401, app.bearerFetch(mapOf("Authorizatıon" to "Bearer demo")).status)
    }

    @Test
    fun challengesRejectedTokensWithoutRunningTheProtectedHandler() {
        val users = BearerAuthentication<String>()
        val app = koyane {
            install(users) { verify { null } }
            install(Http) { get("/", authenticated(users) { error("must not run") }) }
            install(Hooks { onResponseReady { assertNull(principalOrNull(users)) } })
        }
        val response = app.bearerFetch(mapOf("Authorization" to "Bearer rejected"))
        assertEquals(401, response.status)
        assertEquals("Bearer realm=\"koyane\", error=\"invalid_token\"", response.headers["WWW-Authenticate"])
    }

    @Test
    fun requiresAVerifierAndAHeaderSafeRealm() {
        val users = BearerAuthentication<String>()
        assertFailsWith<IllegalArgumentException> { koyane { install(users) } }
        for (realm in listOf("bad\r\nheader", "bad\tvalue", "bad\u0000", "bad\u007f", "日本語")) {
            assertFailsWith<IllegalArgumentException> {
                koyane {
                    install(users) {
                        this.realm = realm
                        verify { it }
                    }
                }
            }
        }
    }

    @Test
    fun snapshotsConfigurationAndQuotesTheRealm() {
        val users = BearerAuthentication<String>()
        lateinit var configuration: BearerConfiguration<String>
        val app = koyane {
            install(users) {
                configuration = this
                realm = "example \"quoted\" \\ realm"
                verify { "first" }
            }
            install(Http) { get("/", authenticated(users) { text(principal(users)) }) }
        }
        configuration.realm = "changed"
        configuration.verify { error("must use installed verifier") }
        val challenge = "Bearer realm=\"example \\\"quoted\\\" \\\\ realm\""
        assertEquals(challenge, app.bearerFetch().headers["WWW-Authenticate"])
        assertEquals("first", app.bearerFetch(mapOf("Authorization" to "Bearer demo")).body)
    }

    @Test
    fun requiresInstallationAndSuccessfulAuthenticationForRequiredPrincipalLookup() {
        val users = BearerAuthentication<String>()
        assertFailsWith<IllegalStateException> {
            koyane {
                install(users) { verify { it } }
                install(users) { verify { it } }
            }
        }
        val missing = koyane { install(Http) { get("/", authenticated(users) { text("unreachable") }) } }
        assertFailsWith<IllegalStateException> { missing.bearerFetch() }
        val unguarded = koyane {
            install(users) { verify { it } }
            install(Http) {
                get("/") {
                    assertNull(principalOrNull(users))
                    text(principal(users))
                }
            }
        }
        assertFailsWith<IllegalStateException> { unguarded.bearerFetch(mapOf("Authorization" to "Bearer demo")) }
    }

    @Test
    fun keepsPluginInstancesDistinctAndReusesSuccessfulVerificationWithinOneCall() {
        val users = BearerAuthentication<String>()
        val counts = BearerAuthentication<Int>()
        var verified = 0
        val app = koyane {
            install(users) {
                verify {
                    verified++
                    "user"
                }
            }
            install(counts) { verify { 7 } }
            install(Http) {
                get(
                    "/",
                    authenticated(users) { name ->
                        authenticated(users) { repeatedName ->
                            assertEquals(name, repeatedName)
                            authenticated(counts) { count ->
                                text("$name/$count")
                            }.invoke(this)
                        }.invoke(this)
                    },
                )
            }
        }
        repeat(2) {
            assertEquals("user/7", app.bearerFetch(mapOf("Authorization" to "Bearer demo")).body)
        }
        assertEquals(2, verified)
    }

    @Test
    fun doesNotAcceptTokensFromQueryBodyOrCookies() {
        val app = bearerApplication { error("must not verify") }
        val request = HttpRequest(
            "GET",
            "/?access_token=demo",
            mapOf("Cookie" to "access_token=demo"),
            "access_token=demo",
        )
        assertEquals(401, runImmediately { app.fetch(request) }.status)
    }
}

internal fun bearerApplication(verify: suspend (String) -> String?): Application {
    val users = BearerAuthentication<String>()
    return koyane {
        install(users) { verify(verify) }
        install(Http) { get("/", authenticated(users) { user -> text(user) }) }
    }
}

internal fun Application.bearerFetch(headers: Map<String, String> = emptyMap()): HttpResponse =
    runImmediately { fetch(HttpRequest("GET", "/", headers)) }

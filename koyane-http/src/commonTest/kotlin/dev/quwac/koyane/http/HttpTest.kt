package dev.quwac.koyane.http

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.core.koyane
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.uuid.Uuid

private const val UUID = "550e8400-e29b-41d4-a716-446655440000"

class HttpTest {
    private val app = httpApplication {
        get("/colon/:name") { text(param.string("name")) }
        get("/braces/{name}") { text(param.string("name")) }
        get("/orgs/:oid/deps/{did}/users/:uid") {
            val oid: Uuid = param.uuid("oid")
            val did: Long = param.long("did")
            val uid: Int = param.int("uid")
            text("$oid/$did/$uid")
        }
        get("/query") {
            text(
                "${query.long(
                    "page",
                    default = 1L,
                )}/${query.int(
                    "limit",
                    default = 10,
                )}/${query.boolean("enabled", default = true)}/${query.stringOrNull("q")}",
            )
        }
        get("/required") { text(query.string("q")) }
        get("/required-numbers") { text("${query.long("long")}/${query.int("int")}/${query.boolean("boolean")}") }
        get("/headers") { text("${header.string("Authorization")}/${header.stringOrNull("X-Request-Id")}") }
        get("/missing-param") { text(param.string("not-in-route")) }
        post("/body") { HttpResponse(HttpStatus.CREATED.code, body.text()) }
        get("/redirect") { redirect("/login") }
        get("/empty") { empty(HttpStatus.NO_CONTENT) }
    }

    private fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String = "",
    ) = immediate { app.fetch(HttpRequest(method, url, headers, body)) }

    @Test
    fun acceptsBothParameterSyntaxesAndMixedRoutes() {
        assertEquals("Ada", request("/colon/Ada").body)
        assertEquals("Ada", request("/braces/Ada").body)
        assertEquals("$UUID/42/7", request("/orgs/${UUID.uppercase()}/deps/42/users/7").body)
        assertEquals(
            "$UUID/${Long.MAX_VALUE}/${Int.MIN_VALUE}",
            request("/orgs/$UUID/deps/${Long.MAX_VALUE}/users/${Int.MIN_VALUE}").body,
        )
    }

    @Test
    fun rejectsDuplicateParametersAndRouteShapesAtRegistration() {
        for (path in listOf("/users/{id}/friends/:id", "/users/:id/friends/{id}")) {
            assertFailsWith<IllegalArgumentException> { httpApplication { get(path) { text("no") } } }
        }
        assertFailsWith<IllegalArgumentException> {
            httpApplication {
                get("/users/{id}") { text("one") }
                get("/users/:name") { text("two") }
            }
        }
        for (path in listOf("relative", "/users/", "/users/{id", "/users/:bad-name", "/users//id", "/users/%20")) {
            assertFailsWith<IllegalArgumentException> { httpApplication { get(path) { text("no") } } }
        }
    }

    @Test
    fun supportsAnyNumberOfNamedParameters() {
        val many = httpApplication {
            get("/:a/:b/:c/:d/:e/:f/:g/:h/:i/:j") { text(param.int("a").plus(param.int("j")).toString()) }
        }
        assertEquals("11", immediate { many.fetch(HttpRequest("GET", "/1/2/3/4/5/6/7/8/9/10")) }.body)
    }

    @Test
    fun parsesQueriesAndAppliesDefaultsOnlyForMissingValues() {
        assertEquals("1/10/true/null", request("/query").body)
        assertEquals("2/5/false/a b+c", request("/query?page=2&limit=5&enabled=false&q=a+b%2Bc").body)
        assertEquals("2/10/true/", request("/query?page=2&page=9&q=").body)
        assertEquals("9/10/true/null", request("https://example.test/query?page=9").body)
        assertEquals("4/2/true", request("/required-numbers?long=4&int=2&boolean=true").body)
        for (query in listOf(
            "page=",
            "page=bad",
            "limit=2147483648",
            "page=9223372036854775808",
            "enabled=yes",
            "enabled=TRUE",
            "enabled=1",
        )) {
            assertEquals(400, request("/query?$query").status, query)
        }
        assertEquals("", request("/required?q=").body)
        assertEquals(400, request("/required").status)
        assertEquals(400, request("/required-numbers").status)
    }

    @Test
    fun extractsHeadersCaseInsensitively() {
        assertEquals(
            "Bearer synthetic/null",
            request("/headers", headers = mapOf("authorization" to "Bearer synthetic")).body,
        )
        assertEquals(
            "token/r1",
            request("/headers", headers = mapOf("AUTHORIZATION" to "token", "x-request-id" to "r1")).body,
        )
        assertEquals(400, request("/headers").status)
    }

    @Test
    fun exposesBodiesAndBuildsResponses() {
        val response = request("/body", "POST", body = " 42 ")
        assertEquals(HttpResponse(201, " 42 "), response)
        assertEquals(HttpResponse(302, headers = mapOf("Location" to "/login")), request("/redirect"))
        assertEquals(HttpResponse(204), request("/empty"))
        assertFailsWith<IllegalArgumentException> { text("invalid", HttpStatus.NO_CONTENT) }
        assertFailsWith<IllegalArgumentException> { redirect("/login", HttpStatus.OK) }
        assertFailsWith<IllegalArgumentException> { redirect("/login\r\nX-Bad: yes") }
    }

    @Test
    fun rejectsInvalidPathValuesAndMissingRequiredParameters() {
        for (path in listOf(
            "/orgs/no/deps/1/users/2",
            "/orgs/$UUID/deps/abc/users/2",
            "/orgs/$UUID/deps/1/users/2147483648",
            "/missing-param",
        )) {
            assertEquals(400, request(path).status, path)
        }
    }

    @Test
    fun decodesPathSegmentsExactlyOnce() {
        for ((encoded, decoded) in listOf(
            "%E6%97%A5%E6%9C%AC%E8%AA%9E" to "日本語",
            "a%2Fb" to "a/b",
            "a+b" to "a+b",
            "a%20b" to "a b",
            "%252F" to "%2F",
        )) {
            assertEquals(decoded, request("/colon/$encoded").body)
        }
        for (value in listOf("%", "%GG", "%FF", "%C0%AF", "%E3%81")) {
            assertEquals(400, request("/colon/$value").status)
            assertEquals(400, request("/query?q=$value").status)
        }
    }

    @Test
    fun distinguishesPathsAndMethodsAndPrioritizesLiteralSegments() {
        for (path in listOf("/missing", "/colon/", "/colon/a/", "/colon/a/b")) assertEquals(404, request(path).status)
        val wrongMethod = request("/colon/Ada", "POST")
        assertEquals(405, wrongMethod.status)
        assertEquals("GET", wrongMethod.headers["Allow"])
        val routes = httpApplication {
            get("/users/:id/details") { text("parameter") }
            get("/users/me/:action") { text("literal") }
        }
        assertEquals("literal", immediate { routes.fetch(HttpRequest("GET", "/users/me/details")) }.body)
    }

    @Test
    fun exposesTheRequestAndPreservesExplicitResponses() {
        val response = HttpResponse(200, "ok", mapOf("X-Custom" to "yes"))
        val input = HttpRequest("POST", "/raw?a=1", mapOf("Content-Type" to "text/plain"), "raw body")
        val raw = httpApplication {
            post("/raw") {
                assertSame(input, request)
                assertEquals("raw body", body.text())
                response
            }
        }
        assertSame(response, immediate { raw.fetch(input) })
    }

    @Test
    fun optionalTypedValuesRejectPresentInvalidValues() {
        val optional = httpApplication {
            get("/optional") {
                text(
                    "${query.longOrNull(
                        "long",
                    )}/${query.intOrNull("int")}/${query.booleanOrNull("bool")}/${query.uuidOrNull("uuid")}",
                )
            }
        }
        fun check(query: String) = immediate { optional.fetch(HttpRequest("GET", "/optional$query")) }
        assertEquals("null/null/null/null", check("").body)
        assertEquals("1/2/false/$UUID", check("?long=1&int=2&bool=false&uuid=$UUID").body)
        for (query in listOf("?long=x", "?int=x", "?bool=x", "?uuid=x")) assertEquals(400, check(query).status)
    }

    @Test
    fun extractsStdlibAndCustomValuesFromPathsQueriesAndHeaders() {
        val typed = httpApplication {
            get("/typed/{timeout}") {
                val timeout = param.duration("timeout")
                val count = query.uint("count", default = 1u)
                val instant = header.instantOrNull("X-Time")
                val page = query.parse("page", 1, String::toIntOrNull)
                text("$timeout/$count/$instant/$page")
            }
        }
        fun check(path: String, headers: Map<String, String> = emptyMap()) =
            immediate { typed.fetch(HttpRequest("GET", path, headers)) }

        assertEquals("1m 30s/1/null/1", check("/typed/PT1M30S").body)
        assertEquals(
            "1m 30s/4294967295/1970-01-01T00:00:00Z/2",
            check("/typed/90s?count=4294967295&page=2", mapOf("x-time" to "1970-01-01T01:00:00+01:00")).body,
        )
        for (path in listOf("/typed/invalid", "/typed/90s?count=-1", "/typed/90s?count=", "/typed/90s?page=invalid")) {
            assertEquals(400, check(path).status, path)
        }
        assertEquals(400, check("/typed/90s", mapOf("X-Time" to "invalid")).status)
    }

    @Test
    fun supportsSuspensionAndPropagatesApplicationFailures() {
        lateinit var pending: Continuation<HttpResponse>
        val suspended = httpApplication { get("/") { suspendCoroutine { pending = it } } }
        var completed: Result<HttpResponse>? = null
        val operation: suspend () -> HttpResponse = { suspended.fetch(HttpRequest("GET", "/")) }
        operation.startCoroutine(object : Continuation<HttpResponse> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<HttpResponse>) {
                completed = result
            }
        })
        assertNull(completed)
        pending.resume(text("resumed"))
        assertEquals(text("resumed"), completed!!.getOrThrow())
        val broken = httpApplication { get("/") { error("application failure") } }
        assertFailsWith<IllegalStateException> { immediate { broken.fetch(HttpRequest("GET", "/")) } }
    }

    @Test
    fun stopsAtTheFailedExtractionBeforeApplicationSideEffects() {
        var invocations = 0
        val boundary = httpApplication {
            get("/users/{id}") {
                val id = param.long("id")
                invocations++
                text(id.toString())
            }
        }
        assertEquals(400, immediate { boundary.fetch(HttpRequest("GET", "/users/no")) }.status)
        assertEquals(0, invocations)
        assertEquals(200, immediate { boundary.fetch(HttpRequest("GET", "/users/1")) }.status)
        assertEquals(1, invocations)
    }

    @Test
    fun supportsExplicitInstallationAllMethodsAndImmutableRouteSnapshots() {
        lateinit var configuration: HttpConfiguration
        val methods = koyane {
            install(Http) {
                configuration = this
                get("/method") { text("GET") }
                query("/method") { text("QUERY") }
                post("/method") { text("POST") }
                put("/method") { text("PUT") }
                patch("/method") { text("PATCH") }
                delete("/method") { text("DELETE") }
                head("/method") { empty(HttpStatus.NO_CONTENT) }
                options("/method") { empty(HttpStatus.NO_CONTENT) }
            }
        }
        for (method in listOf("GET", "POST", "PUT", "PATCH", "DELETE", "QUERY")) {
            assertEquals(method, immediate { methods.fetch(HttpRequest(method, "/method")) }.body)
        }
        for (method in listOf("HEAD", "OPTIONS")) {
            assertEquals(204, immediate { methods.fetch(HttpRequest(method, "/method")) }.status)
        }
        configuration.get("/late") { text("late") }
        assertEquals(404, immediate { methods.fetch(HttpRequest("GET", "/late")) }.status)
    }

    @Test
    fun handlesQueryMethodWithBodyAndQueryParameters() {
        val search = httpApplication {
            query("/search/{id}") {
                text((param.int("id") + query.int("offset") + body.text().toInt()).toString())
            }
        }
        val response = immediate { search.fetch(HttpRequest("QUERY", "/search/1?offset=2", body = "3")) }
        assertEquals("6", response.body)
        assertEquals(200, response.status)
        val wrongMethod = immediate { search.fetch(HttpRequest("GET", "/search/1?offset=2")) }
        assertEquals(405, wrongMethod.status)
        assertEquals("QUERY", wrongMethod.headers["Allow"])
    }
}

private fun <T> immediate(block: suspend () -> T): T {
    var completed: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            completed = result
        }
    })
    return checkNotNull(completed) { "This test expects immediate completion" }.getOrThrow()
}

private fun httpApplication(configure: HttpConfiguration.() -> Unit): Application = koyane {
    install(Http, configure)
}

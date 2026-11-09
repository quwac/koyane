package dev.quwac.koyane.testing

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.core.ApplicationBuilder
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.HttpMethod
import dev.quwac.koyane.http.HttpRequest
import dev.quwac.koyane.http.fetch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest

/** Runs one isolated Koyane application with an in-memory HTTP client. */
fun testApplication(block: suspend TestApplicationScope.() -> Unit): TestResult = runTest {
    runTestApplication(block)
}

class TestApplicationScope internal constructor() {
    private var configuration: (ApplicationBuilder.() -> Unit)? = null
    private var builtApplication: Application? = null

    val client = TestClient(::applicationInstance)

    /** Configures the application used by this test. Call once before sending a request. */
    fun application(configure: ApplicationBuilder.() -> Unit) {
        check(configuration == null && builtApplication == null) { "The test application is already configured" }
        configuration = configure
    }

    private fun applicationInstance(): Application {
        builtApplication?.let { return it }
        val configure = checkNotNull(configuration) { "Configure the test application before sending a request" }
        return koyane(configure).also { builtApplication = it }
    }
}

class TestClient internal constructor(private val application: () -> Application) {
    suspend fun request(
        method: HttpMethod,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        configure: TestRequestBuilder.() -> Unit = {},
    ): TestResponse {
        val request = TestRequestBuilder(url, headers, body).apply(configure).build(method)
        val response = application().fetch(request)
        return TestResponse(response.status, response.headers.toMap(), response.body)
    }

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        configure: TestRequestBuilder.() -> Unit = {},
    ): TestResponse = request(HttpMethod.GET, url, headers, body, configure)

    suspend fun post(
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        configure: TestRequestBuilder.() -> Unit = {},
    ): TestResponse = request(HttpMethod.POST, url, headers, body, configure)

    suspend fun put(
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        configure: TestRequestBuilder.() -> Unit = {},
    ): TestResponse = request(HttpMethod.PUT, url, headers, body, configure)

    suspend fun patch(
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        configure: TestRequestBuilder.() -> Unit = {},
    ): TestResponse = request(HttpMethod.PATCH, url, headers, body, configure)

    suspend fun delete(
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        configure: TestRequestBuilder.() -> Unit = {},
    ): TestResponse = request(HttpMethod.DELETE, url, headers, body, configure)

    suspend fun head(
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        configure: TestRequestBuilder.() -> Unit = {},
    ): TestResponse = request(HttpMethod.HEAD, url, headers, body, configure)

    suspend fun options(
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        configure: TestRequestBuilder.() -> Unit = {},
    ): TestResponse = request(HttpMethod.OPTIONS, url, headers, body, configure)

    suspend fun query(
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        configure: TestRequestBuilder.() -> Unit = {},
    ): TestResponse = request(HttpMethod.QUERY, url, headers, body, configure)
}

class TestRequestBuilder internal constructor(
    private var url: String,
    headers: Map<String, String>,
    var body: String,
) {
    private val headers = linkedMapOf<String, String>().apply {
        headers.forEach { (name, value) -> put(name, value) }
    }
    private val queries = mutableListOf<Pair<String, String>>()

    /** Appends a header value using the Web Headers comma-separated representation. */
    fun header(name: String, value: String) {
        val existing = headers.keys.firstOrNull { it.equals(name, ignoreCase = true) }
        if (existing == null) {
            headers[name] = value
        } else {
            headers[existing] = "${headers.getValue(existing)}, $value"
        }
    }

    /** Appends one URL query parameter using UTF-8 percent encoding. */
    fun query(name: String, value: String) {
        queries += name to value
    }

    internal fun build(method: HttpMethod): HttpRequest = HttpRequest(
        method = method.name,
        url = appendQueries(url, queries),
        headers = headers.toMap(),
        body = body,
    )
}

class TestResponse internal constructor(val status: Int, val headers: Map<String, String>, private val body: String) {
    fun bodyAsText(): String = body

    fun header(name: String): String? = headers.entries.firstOrNull {
        it.key.equals(name, ignoreCase = true)
    }?.value
}

internal suspend fun runTestApplication(block: suspend TestApplicationScope.() -> Unit) {
    TestApplicationScope().block()
}

private fun appendQueries(url: String, queries: List<Pair<String, String>>): String {
    if (queries.isEmpty()) return url
    val separator = when {
        url.endsWith('?') || url.endsWith('&') -> ""
        '?' in url -> "&"
        else -> "?"
    }
    return url + separator + queries.joinToString("&") { (name, value) ->
        "${encodeQueryComponent(name)}=${encodeQueryComponent(value)}"
    }
}

private fun encodeQueryComponent(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val code = byte.toInt() and 0xff
        if (code.isUnreserved()) {
            append(code.toChar())
        } else {
            append('%')
            append(code.toString(HEX_RADIX).uppercase().padStart(2, '0'))
        }
    }
}

private fun Int.isUnreserved(): Boolean =
    this in 'a'.code..'z'.code || this in 'A'.code..'Z'.code || this in '0'.code..'9'.code ||
        this == '-'.code || this == '.'.code || this == '_'.code || this == '~'.code

private const val HEX_RADIX = 16

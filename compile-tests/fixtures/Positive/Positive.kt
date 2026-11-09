package fixtures

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.core.AttributeKey
import dev.quwac.koyane.core.Plugin
import dev.quwac.koyane.core.PluginScope
import dev.quwac.koyane.contentnegotiation.ContentNegotiation
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpMethod
import dev.quwac.koyane.cors.Cors
import dev.quwac.koyane.errorhandling.ErrorHandling
import dev.quwac.koyane.http.HttpStatus
import dev.quwac.koyane.http.InputValues
import dev.quwac.koyane.authentication.bearer.BearerAuthentication
import dev.quwac.koyane.authentication.bearer.authenticated
import dev.quwac.koyane.authentication.bearer.principal
import dev.quwac.koyane.authentication.bearer.principalOrNull
import dev.quwac.koyane.http.beforeHandler
import dev.quwac.koyane.http.empty
import dev.quwac.koyane.http.onRequest
import dev.quwac.koyane.http.onResponse
import dev.quwac.koyane.http.onResponseReady
import dev.quwac.koyane.http.onError
import dev.quwac.koyane.http.text
import dev.quwac.koyane.logging.Logging
import dev.quwac.koyane.serialization.kotlinx.json
import dev.quwac.koyane.serialization.kotlinx.receive
import dev.quwac.koyane.serialization.kotlinx.respond
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val requestId = AttributeKey<String>("request id")
private val applicationPrefix = AttributeKey<String>("prefix")
private val listSerializer = ListSerializer(Long.serializer())
private val users = BearerAuthentication<User>()

val app = koyane {
    install(ContentNegotiation) { json() }
    install(ErrorHandling) {
        exception<IllegalArgumentException> { text("invalid", HttpStatus.BAD_REQUEST) }
        exception<Throwable> { empty(HttpStatus.INTERNAL_SERVER_ERROR) }
        status(HttpStatus.NOT_FOUND) { text("missing", HttpStatus.NOT_FOUND) }
        status(HttpStatus.METHOD_NOT_ALLOWED) { empty(HttpStatus.METHOD_NOT_ALLOWED) }
    }
    install(Logging) { sink = {} }
    install(Cors) {
        allowOrigin("https://client.example")
        allowMethod(HttpMethod.PATCH)
        allowHeader("Content-Type")
        exposeHeader("X-Id")
        allowCredentials = true
        maxAgeSeconds = 600L
    }
    install(RequestId) { prefix = "request" }
    install(users) { verify { token -> token.toLongOrNull()?.let(::User) } }
    install(Http) {
        get("/me", authenticated(users) { user ->
            val required: User = user
            val optional: User? = call.principalOrNull(users)
            text("${required.id}/${optional?.id}")
        })
        get("/me/lookup", authenticated(users) { text(principal(users).id.toString()) })
        query("/search") { respond(receive(UserSerializer), UserSerializer) }
        post("/json") {
            val numbers: List<Long> = receive(listSerializer)
            respond(numbers, listSerializer)
        }
        get("/users/{id}") {
            val typedId: Long = param.long("id")
            when (val result = getUser(typedId)) {
                is GetUserResponse.Ok -> text(result.body.id.toString())
                GetUserResponse.NotFound -> empty(HttpStatus.NOT_FOUND)
            }
        }
        get("/inputs/{value}") {
            val byte: Byte = param.byte("value")
            val short: Short = param.short("value")
            val float: Float = query.float("float", default = 1f)
            val double: Double = query.double("double", default = 1.0)
            val ubyte: UByte = query.ubyte("ubyte", default = 1u)
            val ushort: UShort = query.ushort("ushort", default = 1u)
            val uint: UInt = query.uint("uint", default = 1u)
            val ulong: ULong = query.ulong("ulong", default = 1uL)
            val instant: Instant = header.instant("X-Time")
            val duration: Duration = header.duration("X-Timeout")
            val optionalByte: Byte? = query.byteOrNull("byte")
            val optionalShort: Short? = query.shortOrNull("short")
            val optionalFloat: Float? = query.floatOrNull("float")
            val optionalDouble: Double? = query.doubleOrNull("double")
            val optionalUbyte: UByte? = query.ubyteOrNull("ubyte")
            val optionalUshort: UShort? = query.ushortOrNull("ushort")
            val optionalUint: UInt? = query.uintOrNull("uint")
            val optionalUlong: ULong? = query.ulongOrNull("ulong")
            val optionalInstant: Instant? = header.instantOrNull("X-Time")
            val optionalDuration: Duration? = header.durationOrNull("X-Timeout")
            val page: PageNumber = query.pageNumber("page")
            val defaultPage: PageNumber = query.parse("page", PageNumber(1), ::parsePageNumber)
            val optionalPage: PageNumber? = query.parseOrNull("page", ::parsePageNumber)
            text("$byte/$short/$float/$double/$ubyte/$ushort/$uint/$ulong/$instant/$duration")
            text("$optionalByte/$optionalShort/$optionalFloat/$optionalDouble/$optionalUbyte/$optionalUshort")
            text("$optionalUint/$optionalUlong/$optionalInstant/$optionalDuration/$page/$defaultPage/$optionalPage")
        }
        post("/organizations/:oid/users/{uid}") {
            val organizationId: Uuid = param.uuid("oid")
            val userId: Uuid = param.uuid("uid")
            val page: Long = query.long("page", default = 1L)
            val limit: Int = query.int("limit", default = 10)
            val enabled: Boolean = query.boolean("enabled", default = true)
            val q: String? = query.stringOrNull("q")
            val authorization: String? = header.stringOrNull("Authorization")
            val user: User = receive(UserSerializer)
            text("$organizationId/$userId/$page/$limit/$enabled/$q/$authorization")
            respond(user, UserSerializer, status = HttpStatus.CREATED)
        }
    }
}

private class RequestIdConfiguration(var prefix: String = "id")

private object RequestId : Plugin<RequestIdConfiguration, String> {
    override fun createConfiguration() = RequestIdConfiguration()
    override fun install(configuration: RequestIdConfiguration): String = configuration.prefix
    override fun onInstall(scope: PluginScope, extension: String) {
        scope.application.attributes[applicationPrefix] = extension
        scope.onRequest {
            attributes[requestId] = application.attributes[applicationPrefix]
            null
        }
        scope.beforeHandler {
            val prefix: String = application[RequestId]
            val id: String = attributes[requestId]
            text("$prefix/$id")
        }
        scope.onResponse { response ->
            val id = attributes.getOrNull(requestId)
            if (id == null) response else response.copy(headers = response.headers + ("X-Id" to id))
        }
        scope.onResponseReady { response ->
            val status: Int = response.status
            check(status >= 200)
        }
        scope.onError { cause -> if (cause is IllegalArgumentException) dev.quwac.koyane.http.HttpResponse(400) else null }
    }
}

private data class PageNumber(val value: Int)

private fun parsePageNumber(value: String): PageNumber? = value.toIntOrNull()?.let(::PageNumber)

private fun InputValues.pageNumber(name: String): PageNumber = parse(name, ::parsePageNumber)

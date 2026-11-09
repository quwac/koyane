package dev.quwac.koyane.example

import dev.quwac.kagiroi.sql.SqlDatabase
import dev.quwac.koyane.authentication.bearer.BearerAuthentication
import dev.quwac.koyane.authentication.bearer.authenticated
import dev.quwac.koyane.contentnegotiation.ContentNegotiation
import dev.quwac.koyane.core.Application
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.cors.Cors
import dev.quwac.koyane.cors.CorsConfiguration
import dev.quwac.koyane.errorhandling.ErrorHandling
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpMethod
import dev.quwac.koyane.http.HttpStatus
import dev.quwac.koyane.logging.Logging
import dev.quwac.koyane.serialization.kotlinx.json
import dev.quwac.koyane.serialization.kotlinx.receive
import dev.quwac.koyane.serialization.kotlinx.respond
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

private const val BENCHMARK_ITERATIONS = 100_000
private const val CORS_MAX_AGE_SECONDS = 600L
private const val FNV_OFFSET_BASIS = 0x811c9dc5.toInt()
private const val FNV_PRIME = 0x01000193
private val messagesSerializer = ListSerializer(String.serializer())
private val users = BearerAuthentication<ExamplePrincipal>()

data class ExamplePrincipal(val name: String)

@Serializable
data class CreateUserRequest(val name: String)

@Serializable
data class CreateUserResponse(val id: String, val name: String)

fun createApplication(
    database: SqlDatabase,
    verifyToken: suspend (String) -> ExamplePrincipal? = { null },
): Application = koyane {
    val userRepository = UserRepository(database)
    install(ContentNegotiation) {
        json()
    }
    install(Logging)
    install(ErrorHandling)
    install(Cors) {
        configureExampleCors()
    }
    install(users) {
        realm = "example"
        verify(verifyToken)
    }
    install(Http) {
        get("/me", authenticated(users) { user -> text(user.name) })
        get("/benchmark") {
            var checksum = FNV_OFFSET_BASIS
            repeat(BENCHMARK_ITERATIONS) { iteration ->
                checksum = (checksum xor iteration) * FNV_PRIME
            }
            text(checksum.toUInt().toString())
        }
        get("/hello/:name") { text("Hello, ${param.string("name")}!") }
        get("/failure") { error("private failure detail") }
        get("/users/{id}") {
            val user = userRepository.findById(param.long("id"))
            if (user == null) empty(HttpStatus.NOT_FOUND) else text(user.name)
        }
        get("/organizations/:organizationId/users/{userId}") {
            val organizationId = param.uuid("organizationId")
            val userId = param.uuid("userId")
            val page = query.long("page", default = 1L)
            val limit = query.int("limit", default = 10)
            val enabled = query.boolean("enabled", default = true)
            val q = query.stringOrNull("q")
            val authorization = header.string("Authorization")
            val requestId = header.stringOrNull("X-Request-Id")
            text("$organizationId/$userId/$page/$limit/$enabled/$q/$authorization/$requestId")
        }
        post("/numbers/:id") {
            val id = param.long("id")
            val value = receive(Long.serializer())
            respond(id + value, Long.serializer(), status = HttpStatus.CREATED)
        }
        query("/numbers/search") { respond(receive(Long.serializer()), Long.serializer()) }
        post("/json/echo") { respond(receive(messagesSerializer), messagesSerializer) }
        post("/users") {
            val request = receive(CreateUserRequest.serializer())
            respond(
                CreateUserResponse(id = "123", name = request.name),
                CreateUserResponse.serializer(),
            )
        }
        registerTodoRoutes(TodoRepository(database))
    }
}

private fun CorsConfiguration.configureExampleCors() {
    allowOrigin("https://client.example")
    allowMethod(HttpMethod.PUT)
    allowMethod(HttpMethod.DELETE)
    allowMethod(HttpMethod.QUERY)
    allowHeader("Content-Type")
    allowHeader("Authorization")
    exposeHeader("Allow")
    maxAgeSeconds = CORS_MAX_AGE_SECONDS
}

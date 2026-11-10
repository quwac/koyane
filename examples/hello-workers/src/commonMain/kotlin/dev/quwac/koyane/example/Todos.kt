package dev.quwac.koyane.example

import dev.quwac.kagiroi.sql.ConstraintViolation
import dev.quwac.koyane.http.HttpConfiguration
import dev.quwac.koyane.http.HttpStatus
import dev.quwac.koyane.http.InvalidHttpInput
import dev.quwac.koyane.serialization.kotlinx.receive
import dev.quwac.koyane.serialization.kotlinx.respond
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.uuid.Uuid

/**
 * A persisted Todo exposed by the HTTP example.
 *
 * @property id The Todo identifier serialized as a hex-and-dash UUID.
 * @property title The Todo title.
 * @property completed Whether the Todo has been completed.
 */
@Serializable
data class Todo(
    @Serializable(with = UuidStringSerializer::class)
    val id: Uuid,
    val title: String,
    val completed: Boolean,
)

/**
 * JSON input for creating a Todo.
 *
 * @property id The client-supplied hex-and-dash UUID.
 * @property title The title, validated as nonblank by the route.
 */
@Serializable
data class CreateTodoRequest(
    @Serializable(with = UuidStringSerializer::class)
    val id: Uuid,
    val title: String,
)

/**
 * JSON input for replacing a Todo's editable fields.
 *
 * @property title The replacement title, validated as nonblank by the route.
 * @property completed The replacement completion state.
 */
@Serializable
data class UpdateTodoRequest(val title: String, val completed: Boolean)

private val todosSerializer = ListSerializer(Todo.serializer())

private object UuidStringSerializer : KSerializer<Uuid> {
    override val descriptor = PrimitiveSerialDescriptor("Uuid", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Uuid) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Uuid =
        Uuid.parseHexDashOrNull(decoder.decodeString()) ?: throw SerializationException("Invalid UUID")
}

internal fun HttpConfiguration.registerTodoRoutes(repository: TodoRepository) {
    get("/todos") {
        respond(repository.findAll(), todosSerializer)
    }
    get("/todos/:id") {
        val todo = repository.findById(param.uuid("id"))
        if (todo == null) empty(HttpStatus.NOT_FOUND) else respond(todo, Todo.serializer())
    }
    post("/todos") {
        val request = receive(CreateTodoRequest.serializer()).validated()
        val todo = try {
            repository.create(request.id, request.title)
        } catch (_: ConstraintViolation) {
            throw InvalidHttpInput()
        }
        respond(todo, Todo.serializer(), status = HttpStatus.CREATED)
    }
    put("/todos/:id") {
        val request = receive(UpdateTodoRequest.serializer()).validated()
        val todo = repository.update(param.uuid("id"), request.title, request.completed)
        if (todo == null) empty(HttpStatus.NOT_FOUND) else respond(todo, Todo.serializer())
    }
    delete("/todos/:id") {
        if (repository.delete(param.uuid("id"))) {
            empty(HttpStatus.NO_CONTENT)
        } else {
            empty(HttpStatus.NOT_FOUND)
        }
    }
}

private fun CreateTodoRequest.validated(): CreateTodoRequest {
    if (title.isBlank()) throw InvalidHttpInput()
    return this
}

private fun UpdateTodoRequest.validated(): UpdateTodoRequest {
    if (title.isBlank()) throw InvalidHttpInput()
    return this
}

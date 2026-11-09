package dev.quwac.koyane.example

import dev.quwac.kagiroi.schema.Table
import dev.quwac.kagiroi.schema.primaryKey
import dev.quwac.kagiroi.sql.SqlExecution
import dev.quwac.kagiroi.sql.asc
import dev.quwac.kagiroi.sql.deleteFrom
import dev.quwac.kagiroi.sql.eq
import dev.quwac.kagiroi.sql.execute
import dev.quwac.kagiroi.sql.insertInto
import dev.quwac.kagiroi.sql.orderBy
import dev.quwac.kagiroi.sql.select
import dev.quwac.kagiroi.sql.update
import dev.quwac.kagiroi.sql.values
import dev.quwac.kagiroi.sql.where
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlin.uuid.Uuid

private object Todos : Table("todos") {
    val id = uuid("id").primaryKey()
    val title = string("title")
    val completed = boolean("completed")
}

internal class TodoRepository(private val database: SqlExecution) {
    suspend fun findAll(): List<Todo> = database.select(Todos.id, Todos.title, Todos.completed)
        .from(Todos)
        .orderBy(Todos.id.asc())
        .stream()
        .map { row -> Todo(row[Todos.id], row[Todos.title], row[Todos.completed]) }
        .toList()

    suspend fun findById(id: Uuid): Todo? = database.select(Todos.id, Todos.title, Todos.completed)
        .from(Todos)
        .where(Todos.id eq id)
        .oneOrNull { row -> Todo(row[Todos.id], row[Todos.title], row[Todos.completed]) }

    suspend fun create(id: Uuid, title: String): Todo {
        database.insertInto(Todos).values {
            set(Todos.id, id)
            set(Todos.title, title)
            set(Todos.completed, false)
        }.execute()
        return Todo(id, title, completed = false)
    }

    suspend fun update(id: Uuid, title: String, completed: Boolean): Todo? {
        val result = database.update(Todos) {
            set(Todos.title, title)
            set(Todos.completed, completed)
        }.where(Todos.id eq id).execute()
        return if (result.affectedRows == 0L) null else Todo(id, title, completed)
    }

    suspend fun delete(id: Uuid): Boolean =
        database.deleteFrom(Todos).where(Todos.id eq id).execute().affectedRows != 0L
}

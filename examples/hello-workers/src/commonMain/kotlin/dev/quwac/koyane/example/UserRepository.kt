package dev.quwac.koyane.example

import dev.quwac.kagiroi.schema.Table
import dev.quwac.kagiroi.schema.primaryKey
import dev.quwac.kagiroi.sql.SqlExecution
import dev.quwac.kagiroi.sql.eq
import dev.quwac.kagiroi.sql.select
import dev.quwac.kagiroi.sql.where

private object Users : Table("users") {
    val id = long("id").primaryKey()
    val name = string("name")
}

internal data class User(val id: Long, val name: String)

internal class UserRepository(private val database: SqlExecution) {
    suspend fun findById(id: Long): User? = database.select(Users.id, Users.name)
        .from(Users)
        .where(Users.id eq id)
        .oneOrNull { row -> User(row[Users.id], row[Users.name]) }
}

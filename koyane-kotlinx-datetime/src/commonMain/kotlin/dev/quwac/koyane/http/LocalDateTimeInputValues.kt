package dev.quwac.koyane.http

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.parseOrNull

/**
 * Reads [name] as an ISO-8601 local date-time without an offset.
 * Missing or invalid input throws [InvalidHttpInput].
 */
fun InputValues.localDateTime(name: String): LocalDateTime = parse(name, ::parseLocalDateTime)

/**
 * Reads [name] as an ISO-8601 local date-time without an offset.
 * Uses [default] only when missing; invalid present input throws [InvalidHttpInput].
 */
fun InputValues.localDateTime(name: String, default: LocalDateTime): LocalDateTime =
    parse(name, default, ::parseLocalDateTime)

/**
 * Reads [name] as an ISO-8601 local date-time without an offset.
 * Returns null only when missing; invalid present input throws [InvalidHttpInput].
 */
fun InputValues.localDateTimeOrNull(name: String): LocalDateTime? = parseOrNull(name, ::parseLocalDateTime)

private fun parseLocalDateTime(value: String): LocalDateTime? = LocalDateTime.parseOrNull(value)

package dev.quwac.koyane.http

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.parseOrNull

/**
 * Reads [name] as an ISO-8601 local date.
 * Missing or invalid input throws [InvalidHttpInput].
 */
fun InputValues.localDate(name: String): LocalDate = parse(name, ::parseLocalDate)

/**
 * Reads [name] as an ISO-8601 local date.
 * Uses [default] only when missing; invalid present input throws [InvalidHttpInput].
 */
fun InputValues.localDate(name: String, default: LocalDate): LocalDate = parse(name, default, ::parseLocalDate)

/**
 * Reads [name] as an ISO-8601 local date.
 * Returns null only when missing; invalid present input throws [InvalidHttpInput].
 */
fun InputValues.localDateOrNull(name: String): LocalDate? = parseOrNull(name, ::parseLocalDate)

/**
 * Reads [name] as an ISO-8601 local time.
 * Missing or invalid input throws [InvalidHttpInput].
 */
fun InputValues.localTime(name: String): LocalTime = parse(name, ::parseLocalTime)

/**
 * Reads [name] as an ISO-8601 local time.
 * Uses [default] only when missing; invalid present input throws [InvalidHttpInput].
 */
fun InputValues.localTime(name: String, default: LocalTime): LocalTime = parse(name, default, ::parseLocalTime)

/**
 * Reads [name] as an ISO-8601 local time.
 * Returns null only when missing; invalid present input throws [InvalidHttpInput].
 */
fun InputValues.localTimeOrNull(name: String): LocalTime? = parseOrNull(name, ::parseLocalTime)

private fun parseLocalDate(value: String): LocalDate? = LocalDate.parseOrNull(value)

private fun parseLocalTime(value: String): LocalTime? = LocalTime.parseOrNull(value)

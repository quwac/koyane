package dev.quwac.koyane.http

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.parseOrNull

fun InputValues.localDateTime(name: String): LocalDateTime = parse(name, ::parseLocalDateTime)

fun InputValues.localDateTime(name: String, default: LocalDateTime): LocalDateTime =
    parse(name, default, ::parseLocalDateTime)

fun InputValues.localDateTimeOrNull(name: String): LocalDateTime? = parseOrNull(name, ::parseLocalDateTime)

private fun parseLocalDateTime(value: String): LocalDateTime? = LocalDateTime.parseOrNull(value)

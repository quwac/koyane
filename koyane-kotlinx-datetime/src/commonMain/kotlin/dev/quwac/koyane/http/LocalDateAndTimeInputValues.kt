package dev.quwac.koyane.http

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.parseOrNull

fun InputValues.localDate(name: String): LocalDate = parse(name, ::parseLocalDate)

fun InputValues.localDate(name: String, default: LocalDate): LocalDate = parse(name, default, ::parseLocalDate)

fun InputValues.localDateOrNull(name: String): LocalDate? = parseOrNull(name, ::parseLocalDate)

fun InputValues.localTime(name: String): LocalTime = parse(name, ::parseLocalTime)

fun InputValues.localTime(name: String, default: LocalTime): LocalTime = parse(name, default, ::parseLocalTime)

fun InputValues.localTimeOrNull(name: String): LocalTime? = parseOrNull(name, ::parseLocalTime)

private fun parseLocalDate(value: String): LocalDate? = LocalDate.parseOrNull(value)

private fun parseLocalTime(value: String): LocalTime? = LocalTime.parseOrNull(value)

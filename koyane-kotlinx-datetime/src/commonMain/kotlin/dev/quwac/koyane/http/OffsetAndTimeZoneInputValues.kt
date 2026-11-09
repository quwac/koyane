package dev.quwac.koyane.http

import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.parseOrNull

fun InputValues.utcOffset(name: String): UtcOffset = parse(name, ::parseUtcOffset)

fun InputValues.utcOffset(name: String, default: UtcOffset): UtcOffset = parse(name, default, ::parseUtcOffset)

fun InputValues.utcOffsetOrNull(name: String): UtcOffset? = parseOrNull(name, ::parseUtcOffset)

fun InputValues.timeZone(name: String): TimeZone = parse(name, ::parseTimeZoneOrNull)

fun InputValues.timeZone(name: String, default: TimeZone): TimeZone = parse(name, default, ::parseTimeZoneOrNull)

fun InputValues.timeZoneOrNull(name: String): TimeZone? = parseOrNull(name, ::parseTimeZoneOrNull)

private fun parseUtcOffset(value: String): UtcOffset? = UtcOffset.parseOrNull(value)

private fun parseTimeZoneOrNull(value: String): TimeZone? = try {
    TimeZone.of(value)
} catch (_: IllegalTimeZoneException) {
    null
}

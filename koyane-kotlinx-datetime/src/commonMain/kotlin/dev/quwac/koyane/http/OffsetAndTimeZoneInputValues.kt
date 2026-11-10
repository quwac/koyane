package dev.quwac.koyane.http

import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.parseOrNull

/**
 * Reads [name] as a UTC offset using [UtcOffset.parseOrNull].
 * Missing or invalid input throws [InvalidHttpInput].
 */
fun InputValues.utcOffset(name: String): UtcOffset = parse(name, ::parseUtcOffset)

/**
 * Reads [name] as a UTC offset using [UtcOffset.parseOrNull].
 * Uses [default] only when missing; invalid present input throws [InvalidHttpInput].
 */
fun InputValues.utcOffset(name: String, default: UtcOffset): UtcOffset = parse(name, default, ::parseUtcOffset)

/**
 * Reads [name] as a UTC offset using [UtcOffset.parseOrNull].
 * Returns null only when missing; invalid present input throws [InvalidHttpInput].
 */
fun InputValues.utcOffsetOrNull(name: String): UtcOffset? = parseOrNull(name, ::parseUtcOffset)

/**
 * Reads [name] as a time zone ID supported by [TimeZone.of].
 * Missing or invalid input throws [InvalidHttpInput].
 */
fun InputValues.timeZone(name: String): TimeZone = parse(name, ::parseTimeZoneOrNull)

/**
 * Reads [name] as a time zone ID supported by [TimeZone.of].
 * Uses [default] only when missing; invalid present input throws [InvalidHttpInput].
 */
fun InputValues.timeZone(name: String, default: TimeZone): TimeZone = parse(name, default, ::parseTimeZoneOrNull)

/**
 * Reads [name] as a time zone ID supported by [TimeZone.of].
 * Returns null only when missing; invalid present input throws [InvalidHttpInput].
 */
fun InputValues.timeZoneOrNull(name: String): TimeZone? = parseOrNull(name, ::parseTimeZoneOrNull)

private fun parseUtcOffset(value: String): UtcOffset? = UtcOffset.parseOrNull(value)

private fun parseTimeZoneOrNull(value: String): TimeZone? = try {
    TimeZone.of(value)
} catch (_: IllegalTimeZoneException) {
    null
}

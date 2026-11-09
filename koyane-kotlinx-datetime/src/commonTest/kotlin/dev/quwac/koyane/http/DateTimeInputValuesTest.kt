package dev.quwac.koyane.http

import dev.quwac.koyane.core.koyane
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class DateTimeInputValuesTest {
    @Test
    fun extractsLocalDates() {
        assertContract(
            value = "2024-02-29",
            invalid = "2024-02-30",
            expected = LocalDate(2024, 2, 29),
            default = LocalDate(2000, 1, 1),
            required = { name -> localDate(name) },
            withDefault = { name, default -> localDate(name, default) },
            optional = { name -> localDateOrNull(name) },
        )
    }

    @Test
    fun extractsLocalTimes() {
        assertContract(
            value = "23:59:58.123456789",
            invalid = "24:00:00",
            expected = LocalTime(23, 59, 58, 123456789),
            default = LocalTime(0, 0),
            required = { name -> localTime(name) },
            withDefault = { name, default -> localTime(name, default) },
            optional = { name -> localTimeOrNull(name) },
        )
    }

    @Test
    fun extractsLocalDateTimes() {
        assertContract(
            value = "2024-02-29T23:59:58.123456789",
            invalid = "2024-02-30T23:59:58",
            expected = LocalDateTime(2024, 2, 29, 23, 59, 58, 123456789),
            default = LocalDateTime(2000, 1, 1, 0, 0),
            required = { name -> localDateTime(name) },
            withDefault = { name, default -> localDateTime(name, default) },
            optional = { name -> localDateTimeOrNull(name) },
        )
    }

    @Test
    fun extractsUtcOffsets() {
        assertContract(
            value = "+05:30",
            invalid = "+18:01",
            expected = UtcOffset(hours = 5, minutes = 30),
            default = UtcOffset.ZERO,
            required = { name -> utcOffset(name) },
            withDefault = { name, default -> utcOffset(name, default) },
            optional = { name -> utcOffsetOrNull(name) },
        )
    }

    @Test
    fun extractsTimeZones() {
        assertContract(
            value = "UTC",
            invalid = "Not/A_Real_Zone",
            expected = TimeZone.UTC,
            default = TimeZone.of("GMT"),
            required = { name -> timeZone(name) },
            withDefault = { name, default -> timeZone(name, default) },
            optional = { name -> timeZoneOrNull(name) },
        )
    }
}

private fun <T : Any> assertContract(
    value: String,
    invalid: String,
    expected: T,
    default: T,
    required: InputValues.(String) -> T,
    withDefault: InputValues.(String, T) -> T,
    optional: InputValues.(String) -> T?,
) {
    assertInput(200, expected.toString(), value, required)
    assertInput(400, "", null, required)
    assertInput(200, default.toString(), null) { name -> withDefault(name, default) }
    assertInput(200, expected.toString(), value) { name -> withDefault(name, default) }
    assertInput(400, "", invalid, required)
    assertInput(400, "", invalid) { name -> withDefault(name, default) }
    assertInput(200, "null", null, optional)
    assertInput(200, expected.toString(), value, optional)
    assertInput(400, "", invalid, optional)
}

private fun assertInput(status: Int, body: String, value: String?, extract: InputValues.(String) -> Any?) {
    val app = koyane {
        install(Http) {
            get("/input") {
                text(extract(query, "value").toString())
            }
        }
    }
    val url = value?.let { "/input?value=${it.replace("+", "%2B")}" } ?: "/input"
    val response = immediate { app.fetch(HttpRequest("GET", url)) }
    assertEquals(status, response.status)
    assertEquals(body, response.body)
}

private fun <T> immediate(block: suspend () -> T): T {
    var completed: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            completed = result
        }
    })
    return checkNotNull(completed) { "This test expects immediate completion" }.getOrThrow()
}

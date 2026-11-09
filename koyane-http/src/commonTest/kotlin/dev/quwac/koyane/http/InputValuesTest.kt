package dev.quwac.koyane.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class InputValuesTest {
    @Test
    fun extractsBytes() {
        checkExtraction("-128", Byte.MIN_VALUE, 1.toByte(), InputValues::byte, InputValues::byteOrNull)
        checkExtraction("127", Byte.MAX_VALUE, 1.toByte(), InputValues::byte, InputValues::byteOrNull)
        checkInvalid(listOf("-129", "128"), InputValues::byte, InputValues::byteOrNull)
    }

    @Test
    fun extractsShorts() {
        checkExtraction("-32768", Short.MIN_VALUE, 1.toShort(), InputValues::short, InputValues::shortOrNull)
        checkExtraction("32767", Short.MAX_VALUE, 1.toShort(), InputValues::short, InputValues::shortOrNull)
        checkInvalid(listOf("-32769", "32768"), InputValues::short, InputValues::shortOrNull)
    }

    @Test
    fun extractsFloats() {
        checkExtraction("-1.25e2", -125f, 1f, InputValues::float, InputValues::floatOrNull)
    }

    @Test
    fun extractsDoubles() {
        checkExtraction("1.25e-2", 0.0125, 1.0, InputValues::double, InputValues::doubleOrNull)
    }

    @Test
    fun extractsUnsignedBytes() {
        checkExtraction("0", UByte.MIN_VALUE, 1.toUByte(), InputValues::ubyte, InputValues::ubyteOrNull)
        checkExtraction("255", UByte.MAX_VALUE, 1.toUByte(), InputValues::ubyte, InputValues::ubyteOrNull)
        checkInvalid(listOf("-1", "256"), InputValues::ubyte, InputValues::ubyteOrNull)
    }

    @Test
    fun extractsUnsignedShorts() {
        checkExtraction("0", UShort.MIN_VALUE, 1.toUShort(), InputValues::ushort, InputValues::ushortOrNull)
        checkExtraction("65535", UShort.MAX_VALUE, 1.toUShort(), InputValues::ushort, InputValues::ushortOrNull)
        checkInvalid(listOf("-1", "65536"), InputValues::ushort, InputValues::ushortOrNull)
    }

    @Test
    fun extractsUnsignedInts() {
        checkExtraction("0", UInt.MIN_VALUE, 1u, InputValues::uint, InputValues::uintOrNull)
        checkExtraction("4294967295", UInt.MAX_VALUE, 1u, InputValues::uint, InputValues::uintOrNull)
        checkInvalid(listOf("-1", "4294967296"), InputValues::uint, InputValues::uintOrNull)
    }

    @Test
    fun extractsUnsignedLongs() {
        checkExtraction("0", ULong.MIN_VALUE, 1uL, InputValues::ulong, InputValues::ulongOrNull)
        checkExtraction("18446744073709551615", ULong.MAX_VALUE, 1uL, InputValues::ulong, InputValues::ulongOrNull)
        checkInvalid(listOf("-1", "18446744073709551616"), InputValues::ulong, InputValues::ulongOrNull)
    }

    @Test
    fun extractsInstants() {
        val expected = Instant.fromEpochSeconds(0, 123456789)
        val default = Instant.fromEpochSeconds(1)
        for (value in listOf("1970-01-01T00:00:00.123456789Z", "1970-01-01T01:00:00.123456789+01:00")) {
            checkExtraction(value, expected, default, InputValues::instant, InputValues::instantOrNull)
        }
        checkInvalid(
            listOf("2026-02-30T00:00:00Z", "1970-01-01T00:00:00"),
            InputValues::instant,
            InputValues::instantOrNull,
        )
    }

    @Test
    fun extractsDurations() {
        checkExtraction("PT1M30S", 90.seconds, 1.seconds, InputValues::duration, InputValues::durationOrNull)
        checkExtraction("1m 30s", 90.seconds, 1.seconds, InputValues::duration, InputValues::durationOrNull)
        checkExtraction("-1.5m", (-90).seconds, 1.seconds, InputValues::duration, InputValues::durationOrNull)
        checkInvalid(listOf("1", "1 hour"), InputValues::duration, InputValues::durationOrNull)
    }

    @Test
    fun preservesStdlibFloatingPointSpecialValues() {
        val values = InputValues(mapOf("nan" to "NaN", "infinity" to "Infinity", "negative" to "-Infinity")::get)
        assertTrue(values.float("nan").isNaN())
        assertTrue(values.doubleOrNull("nan")!!.isNaN())
        assertEquals(Float.POSITIVE_INFINITY, values.floatOrNull("infinity"))
        assertEquals(Double.NEGATIVE_INFINITY, values.double("negative"))
    }

    @Test
    fun parsesCustomTypes() {
        val values = InputValues(mapOf("value" to "42")::get)
        val missing = InputValues { null }
        assertEquals(PageNumber(42), values.pageNumber("value"))
        assertEquals(PageNumber(42), values.parseOrNull("value", ::parsePageNumber))
        assertFailsWith<InvalidHttpInput> { missing.pageNumber("value") }
        assertNull(missing.parseOrNull("value", ::parsePageNumber))
        for (value in listOf("", "invalid")) {
            val invalid = InputValues(mapOf("value" to value)::get)
            assertFailsWith<InvalidHttpInput> { invalid.pageNumber("value") }
            assertFailsWith<InvalidHttpInput> { invalid.parseOrNull("value", ::parsePageNumber) }
        }
    }

    @Test
    fun appliesCustomDefaultsOnlyForMissingValues() {
        val default = PageNumber(1)
        val missing = InputValues { null }
        assertSame(default, missing.parse("value", default, ::parsePageNumber))
        val values = InputValues(mapOf("value" to "42")::get)
        assertEquals(PageNumber(42), values.parse("value", default, ::parsePageNumber))
        for (value in listOf("", "invalid")) {
            val invalid = InputValues(mapOf("value" to value)::get)
            assertFailsWith<InvalidHttpInput> { invalid.parse("value", default, ::parsePageNumber) }
        }
    }

    @Test
    fun skipsParsersForMissingValuesAndPropagatesParserFailures() {
        val failure = IllegalStateException("parser failure")
        val parser: (String) -> PageNumber? = { throw failure }
        val missing = InputValues { null }
        assertFailsWith<InvalidHttpInput> { missing.parse("value", parser) }
        assertNull(missing.parseOrNull("value", parser))
        assertEquals(PageNumber(1), missing.parse("value", PageNumber(1), parser))
        val values = InputValues(mapOf("value" to "42")::get)
        assertSame(failure, assertFailsWith<IllegalStateException> { values.parse("value", parser) })
        assertSame(failure, assertFailsWith<IllegalStateException> { values.parse("value", PageNumber(1), parser) })
        assertSame(failure, assertFailsWith<IllegalStateException> { values.parseOrNull("value", parser) })
    }
}

private fun <T : Any> checkExtraction(
    value: String,
    expected: T,
    default: T,
    required: InputValues.(String, T?) -> T,
    optional: InputValues.(String) -> T?,
) {
    val values = InputValues(mapOf("value" to value)::get)
    assertEquals(expected, values.required("value", null))
    assertEquals(expected, values.required("value", default))
    assertEquals(expected, values.optional("value"))
    assertFailsWith<InvalidHttpInput> { values.required("missing", null) }
    assertEquals(default, values.required("missing", default))
    assertNull(values.optional("missing"))
    for (invalid in listOf("", "invalid")) {
        val inputs = InputValues(mapOf("value" to invalid)::get)
        assertFailsWith<InvalidHttpInput> { inputs.required("value", null) }
        assertFailsWith<InvalidHttpInput> { inputs.required("value", default) }
        assertFailsWith<InvalidHttpInput> { inputs.optional("value") }
    }
}

private fun <T : Any> checkInvalid(
    values: List<String>,
    required: InputValues.(String, T?) -> T,
    optional: InputValues.(String) -> T?,
) {
    for (value in values) {
        val inputs = InputValues(mapOf("value" to value)::get)
        assertFailsWith<InvalidHttpInput> { inputs.required("value", null) }
        assertFailsWith<InvalidHttpInput> { inputs.optional("value") }
    }
}

private data class PageNumber(val value: Int)

private fun parsePageNumber(value: String): PageNumber? = value.toIntOrNull()?.let(::PageNumber)

private fun InputValues.pageNumber(name: String): PageNumber = parse(name, ::parsePageNumber)

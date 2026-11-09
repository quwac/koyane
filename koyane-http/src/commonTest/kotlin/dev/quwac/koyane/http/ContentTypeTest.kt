package dev.quwac.koyane.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContentTypeTest {
    @Test
    fun parsesAndNormalizesMediaTypesAndParameters() {
        val contentType = ContentType.parse("Application/JSON; Charset=\"utf-8\"; profile=test")
        assertEquals("application", contentType?.type)
        assertEquals("json", contentType?.subtype)
        assertEquals(mapOf("charset" to "utf-8", "profile" to "test"), contentType?.parameters)
        assertEquals("application/json; charset=utf-8; profile=test", contentType.toString())
        assertEquals(true, contentType?.matches(ContentType("application", "json")))
    }

    @Test
    fun rejectsMalformedMediaTypesAndHeaderInjection() {
        for (value in listOf("", "application", "*/json", "application/json; charset", "text/plain\r\nX: y")) {
            assertNull(ContentType.parse(value), value)
        }
    }
}

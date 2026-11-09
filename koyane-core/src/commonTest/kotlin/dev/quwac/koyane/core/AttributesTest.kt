package dev.quwac.koyane.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttributesTest {
    @Test
    fun storesTypedValuesAndUsesKeyIdentity() {
        val count = AttributeKey<Int>("value")
        val label = AttributeKey<String>("value")
        val otherCount = AttributeKey<Int>("value")
        val attributes = Attributes()
        attributes[count] = 1
        attributes[label] = "one"
        val value: Int = attributes[count]
        val text: String = attributes[label]
        assertEquals(1, value)
        assertEquals("one", text)
        assertNull(attributes.getOrNull(otherCount))
        assertFalse(otherCount in attributes)
        assertTrue(count in attributes)
        assertFailsWith<IllegalStateException> { attributes[otherCount] }
        attributes[count] = 2
        assertEquals(2, attributes[count])
        attributes.remove(count)
        assertNull(attributes.getOrNull(count))
        assertEquals("one", attributes[label])
    }

    @Test
    fun storesAreIndependent() {
        val key = AttributeKey<List<String>>("items")
        val first = Attributes()
        val second = Attributes()
        first[key] = listOf("first")
        assertNull(second.getOrNull(key))
        second[key] = listOf("second")
        assertEquals(listOf("first"), first[key])
        assertEquals(listOf("second"), second[key])
    }
}

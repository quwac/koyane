package dev.quwac.koyane.core

/** Keys use instance identity. The name is diagnostic and never used for lookup. */
class AttributeKey<T : Any>(val name: String)

/** A typed store. Share a key instance to share a value; values cannot be null. */
class Attributes {
    private val values = mutableMapOf<AttributeKey<*>, Any>()

    operator fun <T : Any> set(key: AttributeKey<T>, value: T) {
        values[key] = value
    }

    operator fun <T : Any> get(key: AttributeKey<T>): T =
        checkNotNull(getOrNull(key)) { "Missing attribute: ${key.name}" }

    @Suppress("UNCHECKED_CAST") // Invariant keys associate the same type at insertion and lookup.
    fun <T : Any> getOrNull(key: AttributeKey<T>): T? = values[key] as T?

    operator fun contains(key: AttributeKey<*>): Boolean = key in values

    fun remove(key: AttributeKey<*>) {
        values.remove(key)
    }
}

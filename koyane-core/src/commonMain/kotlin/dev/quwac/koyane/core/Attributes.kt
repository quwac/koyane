package dev.quwac.koyane.core

/**
 * Keys use instance identity. The name is diagnostic and never used for lookup.
 *
 * @property name A label used in missing-attribute diagnostics.
 */
class AttributeKey<T : Any>(val name: String)

/** A typed store. Share a key instance to share a value; values cannot be null. */
class Attributes {
    private val values = mutableMapOf<AttributeKey<*>, Any>()

    /** Stores [value] under [key], replacing any value stored with that exact key instance. */
    operator fun <T : Any> set(key: AttributeKey<T>, value: T) {
        values[key] = value
    }

    /** Returns the value for [key], or throws [IllegalStateException] if the key is absent. */
    operator fun <T : Any> get(key: AttributeKey<T>): T =
        checkNotNull(getOrNull(key)) { "Missing attribute: ${key.name}" }

    /** Returns the value for [key], or null if this key instance has no value. */
    @Suppress("UNCHECKED_CAST") // Invariant keys associate the same type at insertion and lookup.
    fun <T : Any> getOrNull(key: AttributeKey<T>): T? = values[key] as T?

    /** Reports whether a value is stored with this exact [key] instance. */
    operator fun contains(key: AttributeKey<*>): Boolean = key in values

    /** Removes the value for [key]; an absent key has no effect. */
    fun remove(key: AttributeKey<*>) {
        values.remove(key)
    }
}

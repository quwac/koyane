package dev.quwac.koyane.runtime

/** Validated access to host environment strings and nested objects. */
interface Environment {
    /** Reads a required string; missing or non-string values are rejected without exposing their values. */
    fun getString(name: String): String = requireNotNull(getStringOrNull(name)) {
        "Environment property '$name' must be a string"
    }

    /** Returns null for absent or null properties; a present non-string value is rejected. */
    fun getStringOrNull(name: String): String?

    /** Views a required object without serializing it or copying native handles. */
    operator fun get(name: String): Environment

    /** Converts a required native object through a library's typed factory, without serialization or framework coupling. */
    fun <T> get(name: String, convert: (Any) -> T): T
}

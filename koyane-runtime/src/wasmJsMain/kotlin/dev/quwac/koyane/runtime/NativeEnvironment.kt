@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.runtime

import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.unsafeCast

/** Views a native host environment object; primitive and null roots are rejected. */
fun nativeEnvironment(native: JsAny): Environment {
    require(isObject(native)) { "Expected an environment object" }
    return NativeEnvironment(native)
}

private class NativeEnvironment(private val native: JsAny) : Environment {
    override fun getStringOrNull(name: String): String? {
        val value = property(native, name)
        if (isAbsent(value)) return null
        require(isString(value)) { "Environment property '$name' must be a string" }
        return value?.unsafeCast<JsString>()?.toString()
    }

    override fun <T> get(name: String, convert: (Any) -> T): T {
        val value = property(native, name)
        require(isObject(value)) { "Environment property '$name' must be an object" }
        return convert(requireNotNull(value))
    }

    override fun get(name: String): Environment {
        val value = property(native, name)
        require(isObject(value)) { "Environment property '$name' must be an object" }
        return NativeEnvironment(requireNotNull(value))
    }
}

@Suppress("UnusedParameter")
private fun property(native: JsAny, name: String): JsAny? = js("native[name]")

@Suppress("UnusedParameter")
private fun isAbsent(value: JsAny?): Boolean = js("value == null")

@Suppress("UnusedParameter")
private fun isObject(value: JsAny?): Boolean = js("value !== null && typeof value === 'object'")

@Suppress("UnusedParameter")
private fun isString(value: JsAny?): Boolean = js("typeof value === 'string'")

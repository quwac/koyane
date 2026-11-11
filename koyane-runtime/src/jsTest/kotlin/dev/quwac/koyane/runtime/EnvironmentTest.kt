@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EnvironmentTest {
    @Test
    fun readsVariablesAndNestedBindingsWithoutSerializingNativeObjects() {
        val env = nativeEnvironment(
            js(
                "({ AUTH_URL: 'https://example.test', HYPERDRIVE: { connectionString: 'local' }, INVALID: 42, EMPTY: null })",
            ),
        )
        assertEquals("https://example.test", env.getString("AUTH_URL"))
        assertEquals("local", env.get("HYPERDRIVE").getString("connectionString"))
        val handle = env.get("HYPERDRIVE") { NativeHandle(it) }
        assertEquals("local", readConnectionString(handle.native))
        assertFailsWith<IllegalArgumentException> { env.get("MISSING", ::NativeHandle) }
        assertFailsWith<IllegalArgumentException> { env.get("AUTH_URL", ::NativeHandle) }
        assertEquals(null, env.getStringOrNull("MISSING"))
        assertEquals(null, env.getStringOrNull("EMPTY"))
        assertFailsWith<IllegalArgumentException> { env.getStringOrNull("INVALID") }
        assertFailsWith<IllegalArgumentException> { env.get("MISSING") }
        assertFailsWith<IllegalArgumentException> { env.getString("MISSING") }
        assertFailsWith<IllegalArgumentException> { env.getString("INVALID") }
        assertFailsWith<IllegalArgumentException> { env.get("AUTH_URL") }
    }
}

private class NativeHandle(val native: Any)

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "UnusedParameter")
private fun readConnectionString(native: Any): String = js("native.connectionString")

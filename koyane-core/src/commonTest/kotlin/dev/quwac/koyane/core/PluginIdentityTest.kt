package dev.quwac.koyane.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class PluginIdentityTest {
    @Test
    fun rejectsUninstalledEqualKeysWithoutPollutingTheInstalledStore() {
        val strings = StorePlugin<String>("values")
        val integers = StorePlugin<Int>("values")
        assertEquals<Any>(strings, integers)
        val app = koyane { install(strings) }
        assertFailsWith<IllegalStateException> { app[integers].add(42) }
        assertEquals(emptyList(), app[strings])
    }

    @Test
    fun installsEqualKeysOfDifferentTypesWithSeparateExtensions() {
        val strings = StorePlugin<String>("values")
        val integers = StorePlugin<Int>("values")
        val app = koyane {
            install(strings)
            install(integers)
        }
        app[strings] += "hello"
        app[integers] += 42
        assertEquals(listOf("hello"), app[strings])
        assertEquals(listOf(42), app[integers])
    }

    @Test
    fun equalCopiesOfTheSameTypeAreDistinctPluginKeys() {
        val first = StorePlugin<String>("values")
        val second = first.copy()
        val missing = first.copy()
        val app = koyane {
            install(first)
            install(second)
        }
        assertNotSame(app[first], app[second])
        app[first] += "first"
        assertEquals(emptyList(), app[second])
        assertFailsWith<IllegalStateException> { app[missing] }
    }

    @Test
    fun changingHashCodeDoesNotBreakLookupOrAllowDuplicateInstallation() {
        val plugin = StorePlugin<String>("before")
        val app = koyane {
            install(plugin)
            plugin.name = "after"
            assertFailsWith<IllegalStateException> { install(plugin) }
        }
        val extension = app[plugin]
        plugin.name = "again"
        assertSame(extension, app[plugin])
    }

    @Test
    fun changingHashCodeDuringConfigurationDoesNotAllowReentrantInstallation() {
        val plugin = StorePlugin<String>("before")
        val app = koyane {
            install(plugin) {
                plugin.name = "after"
                assertFailsWith<IllegalStateException> { install(plugin) }
            }
        }
        assertEquals(emptyList(), app[plugin])
    }

    @Test
    fun nestedEqualKeysDoNotRemoveTheOuterInstallationGuard() {
        val strings = StorePlugin<String>("values")
        val integers = StorePlugin<Int>("values")
        val app = koyane {
            install(strings) {
                install(integers)
                assertFailsWith<IllegalStateException> { install(strings) }
            }
        }
        app[strings] += "hello"
        app[integers] += 42
        assertEquals(listOf("hello"), app[strings])
        assertEquals(listOf(42), app[integers])
    }

    @Test
    fun installationAndLookupNeverInvokePluginEqualityOrHashCode() {
        val first = IdentityOnlyPlugin()
        val second = IdentityOnlyPlugin()
        val app = koyane {
            install(first) {
                install(second)
                assertFailsWith<IllegalStateException> { install(first) }
            }
            assertFailsWith<IllegalStateException> { install(second) }
        }
        assertEquals(Unit, app[first])
        assertEquals(Unit, app[second])
        assertFailsWith<IllegalStateException> { app[IdentityOnlyPlugin()] }
    }
}

private data class StorePlugin<T : Any>(var name: String) : Plugin<Unit, MutableList<T>> {
    override fun createConfiguration() = Unit
    override fun install(configuration: Unit): MutableList<T> = mutableListOf()
}

private class IdentityOnlyPlugin : Plugin<Unit, Unit> {
    override fun createConfiguration() = Unit
    override fun install(configuration: Unit) = Unit
    override fun equals(other: Any?): Boolean = error("Plugin equality must not be invoked")
    override fun hashCode(): Int = error("Plugin hashCode must not be invoked")
}

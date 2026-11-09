package dev.quwac.koyane.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PluginScopeTest {
    private val stateKey = AttributeKey<MutableList<String>>("events")

    private class Configuration(var value: String = "default")
    private class State(val value: String)
    private val producer = object : Plugin<Configuration, State> {
        override fun createConfiguration() = Configuration()
        override fun install(configuration: Configuration) = State(configuration.value)
        override fun onInstall(scope: PluginScope, extension: State) {
            assertSame(extension, scope.application[this])
            scope.application.attributes[stateKey] += extension.value
        }
    }

    @Test
    fun sharesApplicationStateAndTypedPluginExtensions() {
        val consumer = object : Plugin<Unit, Unit> {
            override fun createConfiguration() = Unit
            override fun install(configuration: Unit) = Unit
            override fun onInstall(scope: PluginScope, extension: Unit) {
                val state: State = scope.application[producer]
                scope.application.attributes[stateKey] += "read ${state.value}"
            }
        }
        fun create(value: String) = koyane {
            attributes[stateKey] = mutableListOf()
            install(producer) { this.value = value }
            install(consumer)
        }
        val first = create("first")
        val second = create("second")
        assertEquals(listOf("first", "read first"), first.attributes[stateKey])
        assertEquals(listOf("second", "read second"), second.attributes[stateKey])
        assertEquals("first", first[producer].value)
    }

    @Test
    fun closesTheInstallationScopeAndBuilder() {
        lateinit var captured: PluginScope
        lateinit var builder: ApplicationBuilder
        val plugin = object : Plugin<Unit, Unit> {
            override fun createConfiguration() = Unit
            override fun install(configuration: Unit) = Unit
            override fun onInstall(scope: PluginScope, extension: Unit) {
                captured = scope
            }
        }
        koyane {
            builder = this
            install(plugin)
        }
        assertFailsWith<IllegalStateException> { captured.application }
        assertFailsWith<IllegalStateException> { builder.install(plugin) }
    }

    @Test
    fun duplicateInstallationDoesNotRepeatConfigurationOrHooks() {
        var configurations = 0
        var installations = 0
        val plugin = object : Plugin<Unit, Unit> {
            override fun createConfiguration() = Unit
            override fun install(configuration: Unit) = Unit
            override fun onInstall(scope: PluginScope, extension: Unit) {
                installations++
            }
        }
        koyane {
            install(plugin) { configurations++ }
            assertFailsWith<IllegalStateException> { install(plugin) { configurations++ } }
        }
        assertEquals(1, configurations)
        assertEquals(1, installations)
    }

    @Test
    fun failedInstallationCannotPublishAPartialApplication() {
        lateinit var captured: PluginScope
        val broken = object : Plugin<Unit, Unit> {
            override fun createConfiguration() = Unit
            override fun install(configuration: Unit) = Unit
            override fun onInstall(scope: PluginScope, extension: Unit) {
                captured = scope
                error("failed")
            }
        }
        assertFailsWith<IllegalStateException> {
            koyane {
                assertFailsWith<IllegalStateException> { install(broken) }
            }
        }
        assertFailsWith<IllegalStateException> { captured.application }
    }

    @Test
    fun rejectsReentrantInstallationOfTheSamePlugin() {
        val app = koyane {
            attributes[stateKey] = mutableListOf()
            install(producer) {
                assertFailsWith<IllegalStateException> { install(producer) }
            }
        }
        assertEquals(listOf("default"), app.attributes[stateKey])
    }
}

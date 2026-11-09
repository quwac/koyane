package dev.quwac.koyane.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApplicationTest {
    private class Configuration(var greeting: String = "Hello")
    private object Greeting : Plugin<Configuration, String> {
        override fun createConfiguration() = Configuration()
        override fun install(configuration: Configuration) = configuration.greeting
    }

    @Test
    fun installsAnOrdinaryProtocolIndependentPlugin() {
        val app = koyane { install(Greeting) { greeting = "Hi" } }
        assertEquals("Hi", app[Greeting])
        assertEquals("Hello", koyane { install(Greeting) }[Greeting])
    }

    @Test
    fun rejectsDuplicateAndMissingPlugins() {
        assertFailsWith<IllegalStateException> {
            koyane {
                install(Greeting)
                install(Greeting)
            }
        }
        assertFailsWith<IllegalStateException> { koyane {}[Greeting] }
    }

    @Test
    fun installationExceptionsPropagate() {
        val broken = object : Plugin<Unit, Unit> {
            override fun createConfiguration() = Unit
            override fun install(configuration: Unit): Unit = error("Installation failed")
        }
        assertFailsWith<IllegalStateException> { koyane { install(broken) } }
    }
}

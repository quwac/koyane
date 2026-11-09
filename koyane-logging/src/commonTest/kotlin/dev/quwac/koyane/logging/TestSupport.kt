package dev.quwac.koyane.logging

import dev.quwac.koyane.core.Plugin
import dev.quwac.koyane.core.PluginScope
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

internal class Hooks(private val configure: PluginScope.() -> Unit) : Plugin<Unit, Unit> {
    override fun createConfiguration() = Unit
    override fun install(configuration: Unit) = Unit
    override fun onInstall(scope: PluginScope, extension: Unit) = configure(scope)
}

internal fun <T> runImmediately(block: suspend () -> T): T {
    var completed: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            completed = result
        }
    })
    return checkNotNull(completed) { "This test expects immediate completion" }.getOrThrow()
}

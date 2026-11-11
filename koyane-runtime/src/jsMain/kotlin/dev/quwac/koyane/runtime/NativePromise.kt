@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.js.JsAny
import kotlin.js.Promise

/** Runs a host invocation in its own coroutine; failures reject with a native JavaScript Error on both targets. */
@Suppress("TooGenericExceptionCaught")
fun <T : JsAny?> nativePromise(block: suspend () -> T): Promise<T> = Promise { resolve, reject ->
    CoroutineScope(EmptyCoroutineContext).launch {
        try {
            resolve(block())
        } catch (cause: Throwable) {
            reject(jsError(cause.toString()))
        }
    }
}

@Suppress("UnusedParameter") // The parameter is referenced by the embedded JavaScript.
private fun jsError(message: String): Throwable = js("new Error(message)")

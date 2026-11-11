@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.node

import dev.quwac.koyane.runtime.Environment
import dev.quwac.koyane.runtime.nativeEnvironment
import kotlin.js.JsAny
import kotlin.js.JsModule

@JsModule("node:process")
private external object NodeProcess {
    val env: JsAny
}

internal actual fun nodeEnvironment(): Environment = nativeEnvironment(NodeProcess.env)

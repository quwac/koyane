@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.example.lambdaweb

import kotlin.js.JsAny
import kotlin.js.JsModule

@JsModule("node:process")
private external object NodeProcess {
    val env: ProcessEnvironment
}

private external interface ProcessEnvironment : JsAny {
    @Suppress("VariableNaming", "PropertyName") // Defined by Lambda Web Functions.
    val AWS_LAMBDA_HTTP_ENDPOINT: String?
}

internal actual fun lambdaHttpEndpoint(): String? = NodeProcess.env.AWS_LAMBDA_HTTP_ENDPOINT

@file:OptIn(kotlin.js.ExperimentalJsExport::class, kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.example.lambda

import dev.quwac.koyane.lambda.LambdaAdapter
import dev.quwac.koyane.lambda.LambdaHttpEvent
import dev.quwac.koyane.lambda.LambdaHttpResponse
import kotlin.js.JsExport
import kotlin.js.Promise

private val adapter = LambdaAdapter(createApplication())

/** The Lambda payload 2.0 entry point; invalid event formats reject the returned promise. */
@JsExport
fun handler(event: LambdaHttpEvent): Promise<LambdaHttpResponse> = adapter.handle(event)

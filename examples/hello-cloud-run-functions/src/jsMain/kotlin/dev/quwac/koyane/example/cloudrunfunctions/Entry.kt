@file:OptIn(kotlin.js.ExperimentalJsExport::class, kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.example.cloudrunfunctions

import dev.quwac.koyane.node.NodeAdapter
import dev.quwac.koyane.node.NodeRequest
import dev.quwac.koyane.node.NodeResponse
import kotlin.js.JsAny
import kotlin.js.JsExport
import kotlin.js.Promise

private val adapter = NodeAdapter(createApplication())

/** The Functions Framework entry point; [body] is the original UTF-8 body read by the host. */
@JsExport
fun handle(request: NodeRequest, response: NodeResponse, body: String): Promise<JsAny?> =
    adapter.handle(request, response, body)

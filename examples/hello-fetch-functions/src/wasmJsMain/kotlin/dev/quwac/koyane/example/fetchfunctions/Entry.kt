@file:OptIn(kotlin.js.ExperimentalJsExport::class, kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.example.fetchfunctions

import dev.quwac.koyane.fetch.FetchAdapter
import dev.quwac.koyane.fetch.FetchRequest
import dev.quwac.koyane.fetch.FetchResponse
import kotlin.js.JsExport
import kotlin.js.Promise

private val adapter = FetchAdapter(createApplication())

@JsExport
fun fetch(request: FetchRequest): Promise<FetchResponse> = adapter.handle(request)

@file:OptIn(kotlin.js.ExperimentalJsExport::class, kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.node

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.runtime.Environment
import dev.quwac.koyane.runtime.RequestApplication
import dev.quwac.koyane.runtime.ResourceScope
import dev.quwac.koyane.runtime.nativeEnvironment
import dev.quwac.koyane.runtime.nativePromise
import kotlin.js.JsAny
import kotlin.js.JsExport
import kotlin.js.Promise

private var function: RequestApplication? = null

/** Registers the common factory; a second registration is rejected. */
actual fun nodeFunction(applicationFactory: suspend ResourceScope.(Environment) -> Application) {
    check(function == null) { "An HTTP function is already defined" }
    function = RequestApplication(applicationFactory)
}

/** Native host entry point; factory and release failures reject, while handler failures send a 500 response. */
@JsExport
fun handleNodeRequest(request: NodeRequest, response: NodeResponse, body: String, environment: JsAny): Promise<JsAny?> =
    nativePromise {
        checkNotNull(function) { "No HTTP function is defined; call nodeFunction from main" }
            .handle(
                nativeEnvironment(environment),
            ) { application ->
                NodeAdapter(application).handleRequest(request, response, body)
                null
            }
    }

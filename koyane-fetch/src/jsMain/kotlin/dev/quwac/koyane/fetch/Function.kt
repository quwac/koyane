@file:OptIn(kotlin.js.ExperimentalJsExport::class, kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.fetch

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
actual fun fetchFunction(applicationFactory: suspend ResourceScope.(Environment) -> Application) {
    check(function == null) { "An HTTP function is already defined" }
    function = RequestApplication(applicationFactory)
}

/** Native host entry point; processing and release failures reject the promise. */
@JsExport
fun handleFetchRequest(request: FetchRequest, environment: JsAny): Promise<FetchResponse> = nativePromise {
    checkNotNull(function) { "No HTTP function is defined; call fetchFunction from main" }
        .handle(nativeEnvironment(environment)) { application -> FetchAdapter(application).handleRequest(request) }
}

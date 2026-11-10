@file:OptIn(kotlin.js.ExperimentalJsExport::class, kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.workers

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.fetch.FetchAdapter
import dev.quwac.koyane.fetch.FetchHeaders
import dev.quwac.koyane.fetch.FetchRequest
import dev.quwac.koyane.fetch.FetchResponse
import kotlin.js.JsAny
import kotlin.js.JsExport
import kotlin.js.Promise
import kotlin.js.unsafeCast

/** Native Fetch types shared with other Fetch hosts. */
typealias WorkerRequest = FetchRequest

/** The native Fetch response returned to the Worker host. */
typealias WorkerResponse = FetchResponse

/** The native Fetch headers exposed by a Worker request. */
typealias WorkerHeaders = FetchHeaders

/** The shared Fetch adapter used by the Workers bridge. */
typealias WorkersAdapter = FetchAdapter

private var workerFactory: ((JsAny) -> Application)? = null
private var workerAdapter: WorkersAdapter? = null

/**
 * Defines the single application factory hosted by this Worker module; a second registration throws.
 * The first fetch invokes [applicationFactory] with the host environment and caches the resulting application.
 * The host must supply an environment matching [Environment]; this boundary does not validate its shape.
 */
fun <Environment : JsAny> defineWorker(applicationFactory: (Environment) -> Application) {
    check(workerFactory == null) { "A Worker is already defined" }
    workerFactory = { environment -> applicationFactory(environment.unsafeCast<Environment>()) }
}

/** The Fetch entry point exported to the JavaScript Worker bridge. */
@JsExport
fun fetch(request: WorkerRequest, environment: JsAny): Promise<WorkerResponse> {
    val adapter = workerAdapter ?: WorkersAdapter(
        checkNotNull(workerFactory) { "No Worker is defined; call defineWorker from main" }(environment),
    ).also { workerAdapter = it }
    return adapter.handle(request)
}

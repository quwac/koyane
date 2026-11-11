@file:OptIn(kotlin.js.ExperimentalJsExport::class, kotlin.js.ExperimentalWasmJsInterop::class)

package dev.quwac.koyane.cloudflare.workers

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.fetch.FetchAdapter
import dev.quwac.koyane.fetch.FetchHeaders
import dev.quwac.koyane.fetch.FetchRequest
import dev.quwac.koyane.fetch.FetchResponse
import dev.quwac.koyane.runtime.Environment
import dev.quwac.koyane.runtime.RequestApplication
import dev.quwac.koyane.runtime.ResourceScope
import dev.quwac.koyane.runtime.nativeEnvironment
import dev.quwac.koyane.runtime.nativePromise
import kotlin.js.JsExport
import kotlin.js.Promise
import kotlin.js.unsafeCast

/** Native Fetch types shared with other Fetch hosts. */
typealias CloudflareWorkerRequest = FetchRequest

/** The native Fetch response returned to the Cloudflare Worker host. */
typealias CloudflareWorkerResponse = FetchResponse

/** The native Fetch headers exposed by a Cloudflare Worker request. */
typealias CloudflareWorkerHeaders = FetchHeaders

/** The shared Fetch adapter used by the Cloudflare Workers bridge. */
typealias CloudflareWorkerAdapter = FetchAdapter

private var cloudflareWorkerFactory: ((dynamic) -> Application)? = null
private var cloudflareWorkerAdapter: CloudflareWorkerAdapter? = null
private var requestApplication: RequestApplication? = null

/**
 * Defines the single application factory hosted by this Cloudflare Worker module; a second registration throws.
 * The first fetch invokes [applicationFactory] with the host environment and caches the resulting application.
 * The host must supply an environment matching [Environment]; this boundary does not validate its shape.
 */
fun <Environment : Any> defineCloudflareWorker(applicationFactory: (Environment) -> Application) {
    check(cloudflareWorkerFactory == null && requestApplication == null) { "A Cloudflare Worker is already defined" }
    cloudflareWorkerFactory = { environment -> applicationFactory(environment.unsafeCast<Environment>()) }
}

/** Registers the common request factory without capturing request-owned resources in the module. */
actual fun cloudflareWorker(applicationFactory: suspend ResourceScope.(Environment) -> Application) {
    check(cloudflareWorkerFactory == null && requestApplication == null) { "A Cloudflare Worker is already defined" }
    requestApplication = RequestApplication(applicationFactory)
}

/** The Fetch entry point exported to the JavaScript Cloudflare Worker bridge. */
@JsExport
fun fetch(request: CloudflareWorkerRequest, environment: dynamic): Promise<CloudflareWorkerResponse> {
    requestApplication?.let { applicationFactory ->
        return nativePromise {
            applicationFactory.handle(nativeEnvironment(environment.unsafeCast<kotlin.js.JsAny>())) { application ->
                CloudflareWorkerAdapter(application).handleRequest(request)
            }
        }
    }
    val adapter = cloudflareWorkerAdapter ?: CloudflareWorkerAdapter(
        checkNotNull(
            cloudflareWorkerFactory,
        ) { "No Cloudflare Worker is defined; call cloudflareWorker or defineCloudflareWorker from main" }(environment),
    ).also { cloudflareWorkerAdapter = it }
    return adapter.handle(request)
}

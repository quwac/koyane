package dev.quwac.koyane.cloudflare.workers

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.runtime.Environment
import dev.quwac.koyane.runtime.ResourceScope

/** Registers a Cloudflare Worker factory; resources are owned by each request and only one registration is allowed. */
expect fun cloudflareWorker(applicationFactory: suspend ResourceScope.(Environment) -> Application)

package dev.quwac.koyane.fetch

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.runtime.Environment
import dev.quwac.koyane.runtime.ResourceScope

/** Registers one HTTP function factory; each invocation owns its application resources through response completion. */
expect fun fetchFunction(applicationFactory: suspend ResourceScope.(Environment) -> Application)

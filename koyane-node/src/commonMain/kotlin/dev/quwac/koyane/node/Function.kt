package dev.quwac.koyane.node

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.runtime.Environment
import dev.quwac.koyane.runtime.ResourceScope

/** Registers one HTTP function factory; each invocation owns its application resources through response completion. */
expect fun nodeFunction(applicationFactory: suspend ResourceScope.(Environment) -> Application)

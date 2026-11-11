package dev.quwac.koyane.lambda

import dev.quwac.koyane.core.Application
import dev.quwac.koyane.runtime.Environment
import dev.quwac.koyane.runtime.ResourceScope

/** Registers one HTTP function factory; each invocation owns its application resources through response completion. */
expect fun awsLambdaFunction(applicationFactory: suspend ResourceScope.(Environment) -> Application)

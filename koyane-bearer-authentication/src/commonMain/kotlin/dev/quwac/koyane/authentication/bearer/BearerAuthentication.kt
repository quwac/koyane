package dev.quwac.koyane.authentication.bearer

import dev.quwac.koyane.core.AttributeKey
import dev.quwac.koyane.core.Plugin
import dev.quwac.koyane.http.HandlerScope
import dev.quwac.koyane.http.HttpCall
import dev.quwac.koyane.http.HttpResponse
import dev.quwac.koyane.http.HttpStatus

class BearerConfiguration<P : Any> {
    var realm: String = "koyane"
    private var verifier: (suspend (String) -> P?)? = null

    /** Return null for rejected tokens; exceptions remain application failures. */
    fun verify(verifier: suspend (String) -> P?) {
        this.verifier = verifier
    }

    internal fun build(): BearerAuthenticator<P> {
        require(realm.all { it in ' '..'~' }) { "Expected a printable ASCII realm" }
        return BearerAuthenticator(realm, requireNotNull(verifier) { "A Bearer verifier is required" })
    }
}

/** Keep this typed plugin instance to install it, protect routes, and read principals. */
class BearerAuthentication<P : Any> : Plugin<BearerConfiguration<P>, BearerAuthenticator<P>> {
    override fun createConfiguration(): BearerConfiguration<P> = BearerConfiguration()
    override fun install(configuration: BearerConfiguration<P>): BearerAuthenticator<P> = configuration.build()
}

/** An immutable verifier configuration with a private, application-owned principal key. */
class BearerAuthenticator<P : Any> internal constructor(realm: String, private val verify: suspend (String) -> P?) {
    private val principalKey = AttributeKey<P>("Bearer principal")
    private val challenge = "Bearer realm=\"${realm.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    internal fun principalOrNull(call: HttpCall): P? = call.attributes.getOrNull(principalKey)

    internal suspend fun authenticate(call: HttpCall): HttpResponse? {
        if (principalOrNull(call) != null) return null
        val credentials = bearerCredentials(call)
        return when (credentials) {
            BearerCredentials.Missing -> rejected(HttpStatus.UNAUTHORIZED)

            BearerCredentials.Malformed -> rejected(HttpStatus.BAD_REQUEST, "invalid_request")

            is BearerCredentials.Token -> {
                val principal = verify(credentials.value)
                if (principal == null) {
                    rejected(HttpStatus.UNAUTHORIZED, "invalid_token")
                } else {
                    call.attributes[principalKey] = principal
                    null
                }
            }
        }
    }

    private fun rejected(status: HttpStatus, error: String? = null): HttpResponse = HttpResponse(
        status.code,
        headers = mapOf("WWW-Authenticate" to if (error == null) challenge else "$challenge, error=\"$error\""),
    )
}

/** Protects only the wrapped handler and passes its principal after routing and before-handler hooks. */
fun <P : Any> authenticated(
    plugin: BearerAuthentication<P>,
    handler: suspend HandlerScope.(P) -> HttpResponse,
): suspend HandlerScope.() -> HttpResponse = {
    application[plugin].authenticate(call) ?: handler(principal(plugin))
}

/** Requires the installed plugin and a successful authentication in this call. */
fun <P : Any> HandlerScope.principal(plugin: BearerAuthentication<P>): P =
    checkNotNull(principalOrNull(plugin)) { "No authenticated principal for this plugin" }

fun <P : Any> HandlerScope.principalOrNull(plugin: BearerAuthentication<P>): P? = call.principalOrNull(plugin)

/** Response and error hooks can read the principal after successful verification. */
fun <P : Any> HttpCall.principalOrNull(plugin: BearerAuthentication<P>): P? = application[plugin].principalOrNull(this)

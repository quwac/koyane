package dev.quwac.koyane.authentication.bearer

import dev.quwac.koyane.http.HttpCall

internal sealed interface BearerCredentials {
    data object Missing : BearerCredentials
    data object Malformed : BearerCredentials
    class Token(val value: String) : BearerCredentials
}

private val bearerToken = Regex("[A-Za-z0-9._~+/-]+=*")

internal fun bearerCredentials(call: HttpCall): BearerCredentials {
    val headers = call.request.headers.entries.filter { (name, _) ->
        name.all { it in 'A'..'Z' || it in 'a'..'z' } && name.equals("Authorization", ignoreCase = true)
    }
    if (headers.isEmpty()) return BearerCredentials.Missing
    if (headers.size != 1) return BearerCredentials.Malformed
    val value = headers.single().value.trim(' ', '\t')
    if (value.isEmpty() || value.any { it != '\t' && it !in ' '..'~' }) return BearerCredentials.Malformed
    val scheme = value.takeWhile { it != ' ' && it != '\t' }
    if (!scheme.equals("Bearer", ignoreCase = true)) return BearerCredentials.Missing
    val token = value.drop(scheme.length).trimStart(' ')
    return if (bearerToken.matches(token)) BearerCredentials.Token(token) else BearerCredentials.Malformed
}

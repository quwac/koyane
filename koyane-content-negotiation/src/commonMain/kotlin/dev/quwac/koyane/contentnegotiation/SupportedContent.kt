package dev.quwac.koyane.contentnegotiation

import dev.quwac.koyane.http.ContentType

internal data class SupportedContent<T : Any>(
    val registrationIndex: Int,
    val registration: ContentRegistration,
    val codec: ContentCodec<T>,
)

private data class AcceptRange(val type: String, val subtype: String, val quality: Double, val index: Int) {
    val specificity: Int = when {
        type == "*" -> 0
        subtype == "*" -> 1
        else -> 2
    }

    fun matches(contentType: ContentType): Boolean =
        matchesPart(type, contentType.type) && matchesPart(subtype, contentType.subtype)
}

private data class ResponseCandidate<T : Any>(val supported: SupportedContent<T>, val range: AcceptRange)

internal fun <T : Any> selectResponse(accept: String?, supported: List<SupportedContent<T>>): SupportedContent<T>? {
    if (accept == null) return supported.firstOrNull()
    val ranges = parseAccept(accept) ?: return null
    return supported.mapNotNull { candidate ->
        val governing = ranges.filter { it.matches(candidate.registration.contentType) }
            .maxWithOrNull(
                compareBy<AcceptRange> { it.specificity }
                    .thenBy { it.quality }
                    .thenBy { -it.index },
            )
        governing?.takeIf { it.quality > 0.0 }?.let { ResponseCandidate(candidate, it) }
    }.minWithOrNull(
        compareByDescending<ResponseCandidate<T>> { it.range.quality }
            .thenByDescending { it.range.specificity }
            .thenBy { it.range.index }
            .thenBy { it.supported.registrationIndex },
    )?.supported
}

private fun parseAccept(value: String): List<AcceptRange>? {
    val entries = splitQuoted(value, ',')?.takeUnless { parts -> parts.any { it.isBlank() } }
    val parsed = entries?.mapIndexed(::parseAcceptRange)
    return parsed?.takeIf { null !in it }?.filterNotNull()
}

private fun parseAcceptRange(index: Int, entry: String): AcceptRange? = splitQuoted(entry, ';')?.let { parts ->
    parseMediaRange(parts.first())?.let { (type, subtype) ->
        parseQuality(parts.drop(1))?.let { quality -> AcceptRange(type, subtype, quality, index) }
    }
}

private fun parseMediaRange(value: String): Pair<String, String>? {
    val mediaType = value.trim().split('/').takeIf { it.size == 2 } ?: return null
    val type = mediaType[0].trim().lowercase()
    val subtype = mediaType[1].trim().lowercase()
    val invalidToken = !isRangeToken(type) || !isRangeToken(subtype)
    val invalidWildcard = type == "*" && subtype != "*"
    return if (invalidToken || invalidWildcard) null else type to subtype
}

private fun parseQuality(parameters: List<String>): Double? {
    var quality = 1.0
    var qualitySeen = false
    var valid = true
    for (parameter in parameters) {
        val assignment = parameter.trim().split('=', limit = 2)
        if (assignment.size != 2 || assignment[0].isBlank()) {
            valid = false
        } else if (assignment[0].trim().equals("q", ignoreCase = true)) {
            val parsed = assignment[1].trim().toDoubleOrNull()?.takeIf { it in 0.0..1.0 }
            if (qualitySeen || parsed == null) valid = false else quality = parsed
            qualitySeen = true
        }
    }
    return quality.takeIf { valid }
}

private fun splitQuoted(value: String, separator: Char): List<String>? {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var escaped = false
    for (character in value) {
        when {
            escaped -> {
                current.append(character)
                escaped = false
            }

            quoted && character == '\\' -> {
                current.append(character)
                escaped = true
            }

            character == '"' -> {
                current.append(character)
                quoted = !quoted
            }

            character == separator && !quoted -> {
                parts += current.toString()
                current.clear()
            }

            else -> current.append(character)
        }
    }
    if (quoted || escaped) return null
    parts += current.toString()
    return parts
}

private fun isRangeToken(value: String): Boolean = value == "*" || runCatching {
    ContentType(value, "plain")
}.isSuccess

private fun matchesPart(range: String, value: String): Boolean = range == "*" || range == value

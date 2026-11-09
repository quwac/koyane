package dev.quwac.koyane.http

private const val TOKEN_CHARACTERS = "!#$%&'*+-.^_`|~"

/** A normalized HTTP media type. Parameter names are case insensitive. */
class ContentType(type: String, subtype: String, parameters: Map<String, String> = emptyMap()) {
    val type: String = type.lowercase()
    val subtype: String = subtype.lowercase()
    val parameters: Map<String, String> = parameters.mapKeys { it.key.lowercase() }

    init {
        require(
            isToken(this.type) && this.type != "*" && isToken(this.subtype) && this.subtype != "*",
        ) { "Expected an HTTP media type" }
        require(this.parameters.all { (name, value) -> isToken(name) && isParameterValue(value) }) {
            "Expected valid HTTP media type parameters"
        }
    }

    fun matches(other: ContentType): Boolean = type == other.type && subtype == other.subtype

    override fun equals(other: Any?): Boolean =
        other is ContentType && type == other.type && subtype == other.subtype && parameters == other.parameters

    override fun hashCode(): Int = 31 * (31 * type.hashCode() + subtype.hashCode()) + parameters.hashCode()

    override fun toString(): String = buildString {
        append(type)
        append('/')
        append(subtype)
        parameters.forEach { (name, value) ->
            append("; ")
            append(name)
            append('=')
            append(if (isToken(value)) value else quote(value))
        }
    }

    companion object {
        fun parse(value: String): ContentType? {
            val parts = splitParameters(value)
            return parts?.let(::parseContentType)
        }
    }
}

private fun isToken(value: String): Boolean = value.isNotEmpty() && value.all { character ->
    character.isLetterOrDigit() || character in TOKEN_CHARACTERS
}

private fun parseContentType(parts: List<String>): ContentType? {
    val mediaType = parts.first().trim().split('/').takeIf { it.size == 2 } ?: return null
    return parseParameters(parts.drop(1))?.let { parameters ->
        runCatching { ContentType(mediaType[0].trim(), mediaType[1].trim(), parameters) }.getOrNull()
    }
}

private fun parseParameters(parts: List<String>): Map<String, String>? {
    val parameters = linkedMapOf<String, String>()
    var valid = true
    for (part in parts) {
        val assignment = part.trim().split('=', limit = 2)
        val name = assignment.firstOrNull()?.trim()?.lowercase().orEmpty()
        val parameter = assignment.getOrNull(1)?.trim()?.let(::unquote)
        if (assignment.size != 2 || name in parameters || parameter == null) {
            valid = false
        } else {
            parameters[name] = parameter
        }
    }
    return parameters.takeIf { valid }
}

private fun isParameterValue(value: String): Boolean = value.none { it == '\r' || it == '\n' }

private fun quote(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        if (character == '"' || character == '\\') append('\\')
        append(character)
    }
    append('"')
}

private fun splitParameters(value: String): List<String>? {
    val result = mutableListOf<String>()
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

            character == ';' && !quoted -> {
                result += current.toString()
                current.clear()
            }

            else -> current.append(character)
        }
    }
    if (quoted || escaped) return null
    result += current.toString()
    return result
}

private fun unquote(value: String): String? {
    if (!value.startsWith('"')) return value.takeIf(::isToken)
    if (value.length < 2 || !value.endsWith('"')) return null
    val result = StringBuilder()
    var escaped = false
    for (character in value.substring(1, value.lastIndex)) {
        when {
            escaped -> {
                result.append(character)
                escaped = false
            }

            character == '\\' -> escaped = true

            character == '"' || character == '\r' || character == '\n' -> return null

            else -> result.append(character)
        }
    }
    return if (escaped) null else result.toString()
}

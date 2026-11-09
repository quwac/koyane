package dev.quwac.koyane.http

internal sealed interface PathSegment {
    data class Static(val value: String) : PathSegment
    data class Parameter(val name: String) : PathSegment
}

internal class RoutePattern(template: String) {
    private val segments: List<PathSegment>
    val shape: List<String?>
    val priority: String

    init {
        require(template.startsWith('/') && (template == "/" || !template.endsWith('/'))) {
            "A route must start with / and have no trailing slash"
        }
        require(template.none { it in "%?#" } && !template.contains("//")) { "Use unencoded route segments" }
        val names = mutableSetOf<String>()
        segments = if (template == "/") {
            emptyList()
        } else {
            template.drop(1).split('/').map { segment ->
                val name = when {
                    segment.startsWith(':') -> segment.drop(1)
                    segment.startsWith('{') && segment.endsWith('}') -> segment.drop(1).dropLast(1)
                    else -> null
                }
                if (name == null) {
                    require(segment.none { it == '{' || it == '}' }) { "Malformed path parameter" }
                    PathSegment.Static(segment)
                } else {
                    require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Invalid path parameter name" }
                    require(names.add(name)) { "Duplicate path parameter: $name" }
                    PathSegment.Parameter(name)
                }
            }
        }
        shape = segments.map { if (it is PathSegment.Static) it.value else null }
        priority = segments.joinToString("") { if (it is PathSegment.Static) "0" else "1" }
    }

    fun match(actual: List<String>): Map<String, String>? {
        if (segments.size != actual.size) return null
        val parameters = mutableMapOf<String, String>()
        val matches = segments.zip(actual).all { (segment, value) ->
            when (segment) {
                is PathSegment.Static -> segment.value == value

                is PathSegment.Parameter -> {
                    parameters[segment.name] = value
                    value.isNotEmpty()
                }
            }
        }
        return parameters.takeIf { matches }
    }
}

internal data class RequestTarget(val segments: List<String>, val query: Map<String, String>)

internal fun parseTarget(url: String): RequestTarget? = try {
    val target = requestTarget(url)
    require('#' !in target)
    val pathname = target.substringBefore('?')
    val segments = if (pathname == "/") emptyList() else pathname.drop(1).split('/').map(::decodeComponent)
    val query = mutableMapOf<String, String>()
    target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.forEach { pair ->
        val name = decodeComponent(pair.substringBefore('=').replace('+', ' '))
        val value = decodeComponent(pair.substringAfter('=', "").replace('+', ' '))
        if (name !in query) query[name] = value
    }
    RequestTarget(segments, query)
} catch (_: IllegalArgumentException) {
    null
} catch (_: CharacterCodingException) {
    null
}

private fun requestTarget(url: String): String {
    if (url.startsWith('/')) return url
    require(url.startsWith("https://") || url.startsWith("http://")) { "Expected an HTTP URL or absolute path" }
    val rest = url.substringAfter("://")
    val start = rest.indexOfAny(charArrayOf('/', '?', '#'))
    require(rest.substring(0, if (start < 0) rest.length else start).isNotEmpty())
    if (start < 0) return "/"
    val target = rest.substring(start)
    return if (target.startsWith('/')) target else "/$target"
}

private const val HEX_RADIX = 16
private const val BITS_PER_HEX_DIGIT = 4
private const val ESCAPED_BYTE_LENGTH = 3

private fun decodeComponent(value: String): String = buildString {
    var index = 0
    while (index < value.length) {
        if (value[index] != '%') {
            append(value[index++])
        } else {
            val bytes = mutableListOf<Byte>()
            while (index < value.length && value[index] == '%') {
                require(index + 2 < value.length)
                val high = value[index + 1].digitToIntOrNull(HEX_RADIX)
                val low = value[index + 2].digitToIntOrNull(HEX_RADIX)
                require(high != null && low != null)
                bytes += ((high shl BITS_PER_HEX_DIGIT) or low).toByte()
                index += ESCAPED_BYTE_LENGTH
            }
            append(bytes.toByteArray().decodeToString(throwOnInvalidSequence = true))
        }
    }
}

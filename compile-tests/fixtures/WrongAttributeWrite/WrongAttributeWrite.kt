package fixtures

import dev.quwac.koyane.core.AttributeKey
import dev.quwac.koyane.core.Attributes

val count = AttributeKey<Int>("count")
val attributes = Attributes().apply { this[count] = "not an Int" }

package fixtures

import dev.quwac.koyane.core.AttributeKey
import dev.quwac.koyane.core.Attributes

val attributes = Attributes()
val name = AttributeKey<String>("name")
val count: Int = attributes[name]

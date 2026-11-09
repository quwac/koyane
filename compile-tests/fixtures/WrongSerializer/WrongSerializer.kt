package fixtures

import dev.quwac.koyane.contentnegotiation.ContentCodec
import dev.quwac.koyane.serialization.kotlinx.KotlinxJsonCodec
import kotlinx.serialization.builtins.serializer

val codec: ContentCodec<String> = KotlinxJsonCodec(Int.serializer())

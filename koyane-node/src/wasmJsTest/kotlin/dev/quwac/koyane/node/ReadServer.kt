package dev.quwac.koyane.node

import kotlinx.coroutines.await
import kotlin.js.JsString
import kotlin.js.Promise

internal actual suspend fun readServer(url: String): String = fetchText(url).await().toString()

@Suppress("UnusedParameter")
private fun fetchText(url: String): Promise<JsString> = js(
    "fetch(url, { headers: { Connection: 'close' } }).then(response => response.text())",
)

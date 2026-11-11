package dev.quwac.koyane.node

import kotlinx.coroutines.await
import kotlin.js.Promise

internal actual suspend fun readServer(url: String): String = fetchText(url).await()

@Suppress("UnusedParameter")
private fun fetchText(url: String): Promise<String> = js(
    "fetch(url, { headers: { Connection: 'close' } }).then(response => response.text())",
)

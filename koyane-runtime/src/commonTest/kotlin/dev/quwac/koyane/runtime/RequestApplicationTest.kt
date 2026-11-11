package dev.quwac.koyane.runtime

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import dev.quwac.koyane.http.HttpRequest
import dev.quwac.koyane.http.fetch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestApplicationTest {
    @Test
    fun givesEachRequestItsOwnResourcesAndCompletesBeforeRelease() = runTest {
        val released = mutableListOf<Int>()
        var acquired = 0
        val environment = object : Environment {
            override fun getStringOrNull(name: String): String = "hello"
            override fun get(name: String): Environment = this
            override fun <T> get(name: String, convert: (Any) -> T): T = convert(this)
        }
        val worker = RequestApplication { env ->
            val id = resource({ ++acquired }) { released += it }
            koyane { install(Http) { get("/") { text("${env.getString("GREETING")}:$id") } } }
        }
        val responses = (1..2).map {
            async { worker.handle(environment) { app -> app.fetch(HttpRequest("GET", "/")) } }
        }.awaitAll()
        assertEquals(setOf("hello:1", "hello:2"), responses.map { it.body }.toSet())
        assertEquals(listOf(1, 2), released.sorted())
    }
}

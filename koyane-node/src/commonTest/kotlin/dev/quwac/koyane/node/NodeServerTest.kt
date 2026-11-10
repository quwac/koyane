package dev.quwac.koyane.node

import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NodeServerTest {
    @Test
    fun listensOnAnAvailablePortAndCloses() = runTest {
        val application = koyane { install(Http) }
        val server = application.listen(port = 0)
        assertTrue(server.port > 0)
        server.close()
    }

    @Test
    fun reportsBindFailures() = runTest {
        val application = koyane { install(Http) }
        val first = application.listen(port = 0)
        try {
            assertFailsWith<IllegalStateException> { application.listen(port = first.port) }
        } finally {
            first.close()
        }
    }

    @Test
    fun rejectsMissingHttpAndInvalidListenAddresses() = runTest {
        assertFailsWith<IllegalStateException> { koyane {}.listen(port = 0) }
        val application = koyane { install(Http) }
        assertFailsWith<IllegalArgumentException> { application.listen(host = "") }
        assertFailsWith<IllegalArgumentException> { application.listen(port = -1) }
    }
}

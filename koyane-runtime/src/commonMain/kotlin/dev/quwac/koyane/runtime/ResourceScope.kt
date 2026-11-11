package dev.quwac.koyane.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns explicitly acquired resources until close; resources must not escape their owner's lifetime. */
open class ResourceScope {
    private val access = Mutex()
    private val releases = mutableListOf<suspend () -> Unit>()
    private val acquisitionsFinished = CompletableDeferred<Unit>()
    private val closed = CompletableDeferred<List<Throwable>>()
    private var active = true
    private var acquiring = 0

    /**
     * Acquires a resource and registers its release in reverse acquisition completion order.
     * If close starts during acquisition, the resource is registered for release and this call fails.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun <T> resource(create: suspend () -> T, release: suspend (T) -> Unit): T {
        access.withLock {
            check(active) { "Resource scope is closed" }
            acquiring++
        }
        val value = try {
            create()
        } catch (cause: Throwable) {
            finishAcquisition(null)
            throw cause
        }
        check(finishAcquisition { release(value) }) { "Resource scope closed during acquisition" }
        return value
    }

    private suspend fun finishAcquisition(release: (suspend () -> Unit)?): Boolean = withContext(NonCancellable) {
        access.withLock {
            if (release != null) releases += release
            acquiring--
            if (!active && acquiring == 0) acquisitionsFinished.complete(Unit)
            active
        }
    }

    /**
     * Waits for pending acquisitions, then releases resources once, including after cancellation.
     * Concurrent close calls wait for the same releases; later acquisitions are rejected.
     * An existing [originalFailure] stays primary and receives release failures as suppressed exceptions.
     * Without an existing failure, a release failure is thrown after all releases have run.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun close(originalFailure: Throwable? = null) = withContext(NonCancellable) {
        val ownsClose = access.withLock {
            if (active) {
                active = false
                if (acquiring == 0) acquisitionsFinished.complete(Unit)
                true
            } else {
                false
            }
        }
        if (ownsClose) {
            acquisitionsFinished.await()
            val pending = access.withLock { releases.asReversed().toList().also { releases.clear() } }
            val failures = mutableListOf<Throwable>()
            for (release in pending) {
                try {
                    release()
                } catch (cause: Throwable) {
                    failures += cause
                }
            }
            closed.complete(failures)
        }
        var failure = originalFailure
        for (cause in closed.await()) {
            if (failure == null) {
                failure = cause
            } else if (failure !== cause && failure.suppressedExceptions.none { it === cause }) {
                failure.addSuppressed(cause)
            }
        }
        if (originalFailure == null) failure?.let { throw it }
    }
}

/** Runs an operation and releases all acquired resources on success, failure, and cancellation. */
// Preserve the operation's failure while recording release failures as suppressed.
@Suppress("TooGenericExceptionCaught")
suspend fun <T> withResourceScope(block: suspend ResourceScope.() -> T): T {
    val scope = ResourceScope()
    var failure: Throwable? = null
    try {
        return scope.block()
    } catch (cause: Throwable) {
        failure = cause
        throw cause
    } finally {
        scope.close(failure)
    }
}

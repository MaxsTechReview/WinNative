package com.winlator.cmod.runtime.content.component

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * One-at-a-time handoff between [ComponentInstaller] (running on a background thread) and the
 * XServer session that is launched to run an installer / regsvr32 inside the container.
 *
 * The installer thread calls [begin] then [await]; the session, when its dependency guest program
 * terminates, calls [complete] with the exit code (see XServerDisplayActivity terminationCallback).
 */
object DependencyInstallBridge {
    private val lock = Any()

    @Volatile private var latch: CountDownLatch? = null

    @Volatile private var exitCode: Int = -1

    fun begin() {
        synchronized(lock) {
            latch = CountDownLatch(1)
            exitCode = -1
        }
    }

    /** Called by the boot session when the dependency program terminates. */
    @JvmStatic
    fun complete(code: Int) {
        synchronized(lock) {
            val l = latch ?: return
            if (l.count == 0L) return
            exitCode = code
            l.countDown()
        }
    }

    /** Blocks up to [timeoutMs]; returns the program's exit code, or null on timeout. */
    fun await(timeoutMs: Long): Int? {
        val l = synchronized(lock) { latch } ?: return null
        val finished = l.await(timeoutMs, TimeUnit.MILLISECONDS)
        return synchronized(lock) {
            latch = null
            if (finished) exitCode else null
        }
    }
}

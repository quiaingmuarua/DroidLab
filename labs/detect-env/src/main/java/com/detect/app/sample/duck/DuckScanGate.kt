package com.detect.app.sample.duck

import java.util.concurrent.atomic.AtomicBoolean

/** UI and HTTP share one scan lifetime, including SDK cleanup and cancellation. */
internal class DuckScanGate {
    private val running = AtomicBoolean()

    suspend fun <T> run(scan: suspend () -> T): T {
        check(running.compareAndSet(false, true)) { "A Duck scan is already running" }
        try {
            return scan()
        } finally {
            running.set(false)
        }
    }
}

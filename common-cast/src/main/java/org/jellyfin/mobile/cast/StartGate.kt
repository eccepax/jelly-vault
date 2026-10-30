package org.jellyfin.mobile.cast

/**
 * Work waiting for the Cast framework to start: run once it has, at once after. If it can't start,
 * what's waiting is dropped and nothing more is kept, since each holds a playback service's session
 * and player. Main thread only, like the framework.
 */
internal class StartGate {
    private var state = State.STARTING
    private val pending = mutableListOf<() -> Unit>()

    private enum class State { STARTING, STARTED, FAILED }

    /** How many are waiting. */
    val waiting: Int get() = pending.size

    /**
     * Runs [onReady] once started; the answer takes it back while it's still waiting (its service has
     * gone), and is null when nothing is: it ran at once, or never will.
     */
    fun whenReady(onReady: () -> Unit): (() -> Unit)? = when (state) {
        State.STARTED -> {
            onReady()
            null
        }
        State.STARTING -> {
            pending += onReady
            { pending.remove(onReady) }
        }
        State.FAILED -> null
    }

    fun started() {
        if (state != State.STARTING) return
        state = State.STARTED
        val ready = pending.toList()
        pending.clear()
        ready.forEach { it() }
    }

    fun failed() {
        state = State.FAILED
        pending.clear()
    }
}

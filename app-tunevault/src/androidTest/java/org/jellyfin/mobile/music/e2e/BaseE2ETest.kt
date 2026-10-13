package org.jellyfin.mobile.music.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.jellyfin.mobile.music.data.TuneDatabase
import org.jellyfin.mobile.music.offline.ServerConnectivity
import org.jellyfin.mobile.music.playback.PlaybackController
import org.junit.After
import org.junit.Before
import org.koin.core.context.GlobalContext

/**
 * Shared harness for instrumented e2e tests. Runs against the real local Vault server (see the
 * `e2e.*` [local.properties] entries wired through `testInstrumentationRunnerArguments` in
 * `app-tunevault/build.gradle.kts`) rather than a mocked one — same approach as the sibling apps'
 * e2e suites. There is no isolated test server, so every test clears the app's persisted state
 * before *and* after running so it always starts (and leaves the emulator) logged out.
 *
 * **Device prerequisite**: animations disabled (`scripts/run-e2e-tests.sh` does this), or Espresso
 * throws `Animations or transitions are enabled on the target device`.
 */
abstract class BaseE2ETest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun resetAppState() {
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        // NOT context.deleteDatabase(...): the app process (and its Koin-held Room singleton) stays
        // alive across test methods, so deleting the file under an open connection leaves a
        // schema-less file behind. clearAllTables() keeps the connection/schema valid.
        GlobalContext.get().get<TuneDatabase>().clearAllTables()
        // Playback (a Koin singleton + the PlaybackService) also outlives each test; without this
        // the previous test's queue is still loaded and its mini-player already on screen.
        onMain { GlobalContext.get().get<PlaybackController>().stop() }
        // Online/offline is app-wide too: after a test that took the Vault away, the app only
        // notices it's back at its next check. Starting before then, a test would see the
        // downloads-only library (empty, once downloads are cleared) instead of the server's.
        val connectivity = GlobalContext.get().get<ServerConnectivity>()
        val deadline = System.currentTimeMillis() + ServerConnectivity.RETRY_INTERVAL_MS * 2
        while (connectivity.isOffline.value && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MS)
        check(!connectivity.isOffline.value) { "The app still thinks the Vault is unreachable" }
    }

    protected fun e2eHostname(): String = instrumentationArg("e2eHostname")
    protected fun e2eUsername(): String = instrumentationArg("e2eUsername")
    protected fun e2ePassword(): String = instrumentationArg("e2ePassword")

    private fun instrumentationArg(key: String): String =
        InstrumentationRegistry.getArguments().getString(key).orEmpty()

    /** Reaches into the real app's Koin container running in-process. */
    protected inline fun <reified T : Any> koinGet(): T = GlobalContext.get().get()

    /** Runs [block] on the main thread (MediaController-backed objects must be used there). */
    protected fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
}

private const val POLL_MS = 250L

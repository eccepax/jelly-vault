package org.jellyfin.mobile.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.jellyfin.mobile.data.JellyfinDatabase
import org.junit.After
import org.junit.Before
import org.koin.core.context.GlobalContext

/**
 * Shared harness for instrumented e2e tests. Runs against the real local Vault server (see the
 * `e2e.*` [local.properties] entries wired through `testInstrumentationRunnerArguments` in
 * `app-audiovault/build.gradle.kts`) rather than a mocked one — there is no isolated test server,
 * so every test clears the app's persisted state before *and* after running so it always starts
 * (and leaves the emulator) logged out, regardless of what ran before it.
 *
 * **Device prerequisite**: the target device/emulator must have animations disabled, or Espresso
 * throws `Animations or transitions are enabled on the target device`:
 * ```
 * adb shell settings put global window_animation_scale 0
 * adb shell settings put global transition_animation_scale 0
 * adb shell settings put global animator_duration_scale 0
 * ```
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
        // NOT context.deleteDatabase(...): the app process (and its Koin-held Room singleton)
        // stays alive across test methods in the same instrumentation run, so deleting the backing
        // file out from under an already-open Room connection leaves a schema-less file behind —
        // the next query fails with "no such table". clearAllTables() is Room's own supported way
        // to wipe data while keeping the connection/schema valid.
        GlobalContext.get().get<JellyfinDatabase>().clearAllTables()
    }

    protected fun e2eHostname(): String = instrumentationArg("e2eHostname")
    protected fun e2eUsername(): String = instrumentationArg("e2eUsername")
    protected fun e2ePassword(): String = instrumentationArg("e2ePassword")

    private fun instrumentationArg(key: String): String =
        InstrumentationRegistry.getArguments().getString(key).orEmpty()

    /**
     * Reaches into the real app's Koin container running in-process — useful for asserting on
     * state (e.g. a persisted preference) that isn't otherwise easily readable from the UI, same
     * technique [resetAppState] already uses for the database.
     */
    protected inline fun <reified T : Any> koinGet(): T = GlobalContext.get().get()
}

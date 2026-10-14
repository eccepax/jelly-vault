package org.jellyfin.mobile.testing.endtoend

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.koin.core.context.GlobalContext

/**
 * Shared harness for the apps' instrumented e2e tests. They run against the real local Vault (the
 * `e2e.*` local.properties entries, passed as instrumentation runner arguments) rather than a mocked
 * one. There is no isolated test server, so every test clears the app's persisted state before *and*
 * after running, and always starts (and leaves the emulator) signed out.
 *
 * **Device prerequisite**: animations disabled (`scripts/run-e2e-tests.sh` does this), or Espresso
 * throws `Animations or transitions are enabled on the target device`.
 */
abstract class VaultE2ETest {

    protected val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * Clears the app's own state, which outlives each test in the same process: its Room database
     * (use `clearAllTables()`, never `deleteDatabase()`: the Koin-held connection stays open, and
     * deleting the file under it leaves a schema-less database behind) and any singletons that
     * would show the previous test's state.
     */
    protected abstract fun clearAppState()

    @Before
    @After
    fun resetAppState() {
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        clearAppState()
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

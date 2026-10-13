package org.jellyfin.mobile.common.setup

import android.app.Application
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.operations.UserApi
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LoginFragmentTest {

    private val apiClient = mockk<ApiClient>(relaxed = true)
    private val loginStarted = CompletableDeferred<Unit>()
    private val loginCancelled = CompletableDeferred<Unit>()

    @Before
    fun setUp() {
        mockkStatic(API_CLIENT_EXTENSIONS)
        every { apiClient.userApi } returns UserApi(apiClient)
        // The login request never answers, as if the server were slow; it only ends by being cancelled
        coEvery { apiClient.request(any(), any(), any(), any(), any()) } coAnswers {
            loginStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                loginCancelled.complete(Unit)
            }
        }
        val appPreferences = mockk<AppPreferences>(relaxed = true)
        every { appPreferences.currentServerId } returns 1L

        startKoin {
            modules(
                module {
                    single { apiClient }
                    single { mockk<ApiClientController>(relaxed = true) }
                    single { appPreferences }
                    viewModel { mockk<MainViewModel>(relaxed = true) }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkStatic(API_CLIENT_EXTENSIONS)
    }

    @Test
    fun `leaving the login screen while a login is in flight does not crash`() {
        // A crash inside a coroutine goes to the thread's uncaught-exception handler, which on a phone kills
        // the app but in a JVM test only prints — so capture it to fail the test instead
        var crash: Throwable? = null
        val mainThread = Thread.currentThread()
        val originalHandler = mainThread.uncaughtExceptionHandler
        mainThread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e -> crash = e }

        val scenario = launchFragmentInContainer<LoginFragment>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<EditText>(R.id.editUsername).setText("alice")
            fragment.requireView().findViewById<Button>(R.id.buttonLogin).performClick()
        }
        shadowOf(Looper.getMainLooper()).idle()
        await(loginStarted)

        // Destroys the fragment's view (e.g. the user navigated away) while the login is still waiting
        scenario.moveToState(Lifecycle.State.CREATED)

        // The request runs off the main thread; once it is cancelled, the login coroutine finishes on the
        // main looper — which is where touching the destroyed view would throw
        await(loginCancelled)
        repeat(SETTLE_ROUNDS) {
            Thread.sleep(POLL_INTERVAL_MS)
            shadowOf(Looper.getMainLooper()).idleFor(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
        }
        mainThread.uncaughtExceptionHandler = originalHandler

        crash.shouldBeNull()
    }

    private fun await(signal: CompletableDeferred<Unit>) = runBlocking {
        withTimeout(SIGNAL_TIMEOUT_MS) { signal.await() }
    }

    @Test
    fun `the login screen keeps its content clear of the status and navigation bars`() {
        val scenario = launchFragmentInContainer<LoginFragment>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            val root = fragment.requireView()
            val basePadding = root.paddingTop
            val bars = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, STATUS_BAR_PX, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAV_BAR_PX))
                .build()

            ViewCompat.dispatchApplyWindowInsets(root, bars)

            root.paddingTop shouldBe basePadding + STATUS_BAR_PX
            root.paddingBottom shouldBe basePadding + NAV_BAR_PX
        }
    }

    private companion object {
        const val STATUS_BAR_PX = 63
        const val NAV_BAR_PX = 42
        const val API_CLIENT_EXTENSIONS = "org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt"
        const val SIGNAL_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 10L
        const val SETTLE_ROUNDS = 20
    }
}

package org.jellyfin.mobile.common.setup

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.setup.quickconnect.QuickConnect
import org.jellyfin.mobile.common.setup.quickconnect.SignedInUser
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The sign-in screen as AudioVault and BookVault show it: in a fragment, wired to the server. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LoginFragmentTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val passwordSignIn = mockk<PasswordSignIn>()
    private val apiClientController = mockk<ApiClientController>(relaxed = true)
    private val mainViewModel = mockk<MainViewModel>(relaxed = true)

    @Before
    fun setUp() {
        coEvery { passwordSignIn.signIn(any(), any()) } returns SignedInUser("alice-id", "token")
        val appPreferences = mockk<AppPreferences>(relaxed = true)
        every { appPreferences.currentServerId } returns 1L

        startKoin {
            modules(
                module {
                    single { passwordSignIn }
                    single { mockk<QuickConnect>(relaxed = true) }
                    single { apiClientController }
                    single { appPreferences }
                    viewModel { mainViewModel }
                    // On the main thread, so the test sees the sign-in finish without waiting on another
                    viewModel { LoginViewModel(get(), get(), get(), get(), Dispatchers.Main) }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun string(resId: Int) = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    @Test
    fun `signing in saves the user and tells the app it's signed in`() {
        launchFragmentInContainer<LoginFragment>(themeResId = R.style.AppTheme).use {
            composeRule.onNodeWithTag(VaultTestTags.LOGIN_USERNAME).performTextInput("alice")
            composeRule.onNodeWithTag(VaultTestTags.LOGIN_PASSWORD).performTextInput("secret")
            composeRule.onNode(hasText(string(R.string.login_button)) and hasClickAction()).performClick()
            composeRule.waitForIdle()
        }

        coVerify { apiClientController.setupUser(1L, "alice-id", "token") }
        coVerify { mainViewModel.refreshState() }
    }

    @Test
    fun `change server goes back to choosing one`() {
        launchFragmentInContainer<LoginFragment>(themeResId = R.style.AppTheme).use {
            composeRule.onNodeWithText(string(R.string.login_change_server)).performClick()
        }

        verify { mainViewModel.resetServer() }
    }

    @Test
    fun `the login screen keeps its content clear of the status and navigation bars`() {
        launchFragmentInContainer<LoginFragment>(themeResId = R.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment ->
                val bars = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, STATUS_BAR_PX, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAV_BAR_PX))
                    .build()
                ViewCompat.dispatchApplyWindowInsets(fragment.requireView(), bars)
            }
            shadowOf(Looper.getMainLooper()).idle()

            val screenHeight = composeRule.onRoot().fetchSemanticsNode().size.height
            val title = composeRule.onNode(hasText(string(R.string.login_title)) and !hasClickAction())
                .fetchSemanticsNode()
            val changeServer = composeRule.onNodeWithText(string(R.string.login_change_server)).fetchSemanticsNode()
            title.boundsInRoot.top shouldBeGreaterThanOrEqual STATUS_BAR_PX.toFloat()
            changeServer.boundsInRoot.bottom shouldBeLessThanOrEqual (screenHeight - NAV_BAR_PX).toFloat()
        }
    }

    private companion object {
        const val STATUS_BAR_PX = 63
        const val NAV_BAR_PX = 42
    }
}

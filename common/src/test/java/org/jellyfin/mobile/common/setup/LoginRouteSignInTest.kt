package org.jellyfin.mobile.common.setup

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.ServerState
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.jellyfin.mobile.common.setup.quickconnect.QuickConnect
import org.jellyfin.mobile.common.setup.quickconnect.SignedInUser
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.testsupport.SCREENSHOT_SDK
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
import org.robolectric.annotation.GraphicsMode

/** The sign-in screen as [org.jellyfin.mobile.common.ui.VaultRoot] shows it, wired to the server. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LoginRouteSignInTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val passwordSignIn = mockk<PasswordSignIn>()
    private val apiClientController = mockk<ApiClientController>(relaxed = true)
    private val serverState = MutableStateFlow<ServerState>(ServerState.Pending)
    private val mainViewModel = mockk<MainViewModel>(relaxed = true) {
        every { serverState } returns this@LoginRouteSignInTest.serverState
    }

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

    // Not common's default Jellyfin blue, so the screen is seen to take the app's own
    private fun showSignIn() = composeRule.setContent {
        AppTheme(VaultBrand.BookVault) { LoginRoute(mainViewModel) }
    }

    private val content: View get() = composeRule.activity.findViewById(android.R.id.content)

    private fun string(resId: Int) = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    @Test
    fun `signing in saves the user and tells the app it's signed in`() {
        showSignIn()
        composeRule.onNodeWithTag(VaultTestTags.LOGIN_USERNAME).performTextInput("alice")
        composeRule.onNodeWithTag(VaultTestTags.LOGIN_PASSWORD).performTextInput("secret")
        composeRule.onNode(hasText(string(R.string.login_button)) and hasClickAction()).performClick()
        composeRule.waitForIdle()

        coVerify { apiClientController.setupUser(1L, "alice-id", "token") }
        coVerify { mainViewModel.refreshState() }
    }

    @Test
    fun `signing in to a server on the internet over plain HTTP warns`() {
        serverState.value = ServerState.Available(ServerEntity(hostname = "http://vault.example.com:8096"))

        showSignIn()
        composeRule.onNodeWithTag(VaultTestTags.LOGIN_INSECURE_SERVER).assertExists()
    }

    @Test
    fun `a server on the home network doesn't`() {
        serverState.value = ServerState.Available(ServerEntity(hostname = "http://192.168.1.5:8096"))

        showSignIn()
        composeRule.onNodeWithTag(VaultTestTags.LOGIN_INSECURE_SERVER).assertDoesNotExist()
    }

    @Test
    fun `change server goes back to choosing one`() {
        showSignIn()
        composeRule.onNodeWithText(string(R.string.login_change_server)).performClick()

        verify { mainViewModel.resetServer() }
    }

    @Test
    fun `the login screen keeps its content clear of the status and navigation bars`() {
        showSignIn()
        val bars = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, STATUS_BAR_PX, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAV_BAR_PX))
            .build()
        // To the screen itself, as the window would hand them down
        ViewCompat.dispatchApplyWindowInsets((content as ViewGroup).getChildAt(0), bars)
        shadowOf(Looper.getMainLooper()).idle()

        val screenHeight = composeRule.onRoot().fetchSemanticsNode().size.height
        val title = composeRule.onNode(hasText(string(R.string.login_title)) and !hasClickAction())
            .fetchSemanticsNode()
        val changeServer = composeRule.onNodeWithText(string(R.string.login_change_server)).fetchSemanticsNode()
        title.boundsInRoot.top shouldBeGreaterThanOrEqual STATUS_BAR_PX.toFloat()
        changeServer.boundsInRoot.bottom shouldBeLessThanOrEqual (screenHeight - NAV_BAR_PX).toFloat()
    }

    @Test
    @Config(sdk = [SCREENSHOT_SDK])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the sign-in button is in the app's own brand`() {
        showSignIn()
        val button = composeRule.onNode(hasText(string(R.string.login_button)) and hasClickAction())
            .fetchSemanticsNode().boundsInRoot
        val pixels = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        content.draw(Canvas(pixels))

        // Just inside the left edge, clear of the rounded corners and the centered label
        pixels.getPixel(button.left.toInt() + EDGE_INSET_PX, button.center.y.toInt()) shouldBe
            VaultBrand.BookVault.accent.toArgb()
    }

    private companion object {
        const val EDGE_INSET_PX = 8
        const val STATUS_BAR_PX = 63
        const val NAV_BAR_PX = 42
    }
}

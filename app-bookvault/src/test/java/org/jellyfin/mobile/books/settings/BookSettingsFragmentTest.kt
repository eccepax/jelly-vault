package org.jellyfin.mobile.books.settings

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.app.EpubFontFamily
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.sdk.model.ClientInfo
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
import org.jellyfin.mobile.common.R as CommonR

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class BookSettingsFragmentTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val mainViewModel = mockk<MainViewModel>(relaxed = true)

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single { BookAppPreferences(context) }
                    single { ClientInfo("BookVault", "9.9.9") }
                    viewModel { mainViewModel }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun launch() = launchFragmentInContainer<BookSettingsFragment>(themeResId = CommonR.style.AppTheme)

    @Test
    fun `about shows the app and version the app was built as`() {
        launch().use {
            composeRule.onNodeWithText("BookVault 9.9.9").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `reading settings chosen here apply to every book`() {
        launch().use {
            composeRule.onNodeWithText("Font size").performClick()
            composeRule.onNodeWithText("150%").performClick()
            composeRule.onNodeWithText("Font family").performClick()
            composeRule.onNodeWithText("Monospace").performClick()
            composeRule.onNodeWithText("Line spacing").performClick()
            composeRule.onNodeWithText("1.8x").performClick()
        }

        val saved = BookAppPreferences(context)
        saved.epubFontScale shouldBe 1.5f
        saved.epubFontFamily shouldBe EpubFontFamily.MONOSPACE
        saved.epubLineSpacing shouldBe 1.8f
    }

    @Test
    fun `the rows show what was just picked`() {
        launch().use {
            composeRule.onNodeWithText("Font size").performClick()
            composeRule.onNodeWithText("150%").performClick()

            composeRule.onNodeWithText("150%").assertExists()
        }
    }

    @Test
    fun `downloads are kept on sign-out unless the user turns on removing them`() {
        BookAppPreferences(context).removeDownloadsOnSignOut shouldBe false

        launch().use {
            composeRule.onNodeWithText(context.getString(CommonR.string.pref_remove_downloads_on_sign_out))
                .performScrollTo()
                .performClick()
        }

        BookAppPreferences(context).removeDownloadsOnSignOut shouldBe true
    }

    @Test
    fun `confirming sign-out signs out`() {
        launch().use {
            composeRule.onNodeWithText("Sign out").performScrollTo().performClick()
            composeRule.onNodeWithText("Yes, sign out").performScrollTo().performClick()
            composeRule.waitForIdle()
        }

        coVerify { mainViewModel.logout() }
    }

    @Test
    fun `the settings screen stays clear of the status and navigation bars`() {
        launch().use { scenario ->
            scenario.onFragment { fragment ->
                val bars = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, STATUS_BAR_PX, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAV_BAR_PX))
                    .build()
                ViewCompat.dispatchApplyWindowInsets(fragment.requireView(), bars)
            }
            shadowOf(Looper.getMainLooper()).idle()

            val title = composeRule.onNodeWithText("Settings").fetchSemanticsNode()
            val screenHeight = composeRule.onRoot().fetchSemanticsNode().size.height
            title.boundsInRoot.top shouldBeGreaterThanOrEqual STATUS_BAR_PX.toFloat()
            val lastRow = composeRule.onNodeWithText("Sign out").performScrollTo().fetchSemanticsNode()
            lastRow.boundsInRoot.bottom shouldBeLessThanOrEqual (screenHeight - NAV_BAR_PX).toFloat()
        }
    }

    private companion object {
        const val STATUS_BAR_PX = 63
        const val NAV_BAR_PX = 126
    }
}

package org.jellyfin.mobile.books.settings

import android.app.Application
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.app.EpubFontFamily
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.testing.downloads.FakeDownloadSettings
import org.jellyfin.sdk.model.ClientInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import org.jellyfin.mobile.common.R as CommonR

/** Settings as BookVault shows them: each choice is saved where the app reads it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class BookSettingsRouteTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val appPreferences = BookAppPreferences(context)
    private val downloads = FakeDownloadSettings()
    private val signingOut = CompletableDeferred<Unit>()
    private var signOuts = 0
    private var signOutFails = false
    private var backs = 0
    private var signedOut = 0

    private val viewModel by lazy {
        BookSettingsViewModel(
            preferences = appPreferences,
            downloads = downloads,
            signOut = {
                signOuts++
                if (signOutFails) error("Couldn't forget the account")
                signingOut.await()
            },
            app = ClientInfo("BookVault", "9.9.9"),
        )
    }

    private fun show() {
        composeRule.setContent {
            AppTheme(VaultBrand.BookVault) {
                BookSettingsRoute(onBack = { backs++ }, onSignedOut = { signedOut++ }, viewModel = viewModel)
            }
        }
    }

    private fun clickText(text: String) = composeRule.onNodeWithText(text).performScrollTo().performClick()

    @Test
    fun `about shows the app and version the app was built as`() {
        show()

        composeRule.onNodeWithText("BookVault 9.9.9").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `reading settings chosen here apply to every book`() {
        show()

        clickText("Font size")
        clickText("150%")
        clickText("Font family")
        clickText("Monospace")
        clickText("Line spacing")
        clickText("1.8x")
        composeRule.waitForIdle()

        val saved = BookAppPreferences(context)
        saved.epubFontScale shouldBe 1.5f
        saved.epubFontFamily shouldBe EpubFontFamily.MONOSPACE
        saved.epubLineSpacing shouldBe 1.8f
    }

    @Test
    fun `the rows show what was just picked`() {
        show()

        clickText("Font size")
        clickText("150%")

        composeRule.onNodeWithText("150%").assertExists()
    }

    @Test
    fun `settings changed elsewhere since it was last open are shown as they are now`() {
        // Opened before (the view model outlives the screen), then changed from the reader
        viewModel.state.value.fontScale shouldBe 1.0f
        appPreferences.epubFontScale = 1.5f
        show()

        composeRule.onNodeWithText("150%").assertExists()
    }

    @Test
    fun `downloads are kept on sign-out unless the user turns on removing them`() {
        appPreferences.removeDownloadsOnSignOut shouldBe false
        show()

        clickText(context.getString(CommonR.string.pref_remove_downloads_on_sign_out))
        composeRule.waitForIdle()

        BookAppPreferences(context).removeDownloadsOnSignOut shouldBe true
    }

    @Test
    fun `switching wi-fi only goes through the download settings, which save and apply it`() {
        downloads.wifiOnly = false
        downloads.events.clear()
        show()

        composeRule.onNodeWithText("Download over Wi-Fi only").performScrollTo().assertIsOff().performClick()
        composeRule.onNodeWithText("Download over Wi-Fi only").assertIsOn()

        downloads.events shouldBe listOf("wifiOnly true")
    }

    @Test
    fun `downloads waiting for wi-fi can be removed though they take no space yet`() {
        downloads.queued.value = true
        show()

        clickText("Remove all downloads")
        clickText("Yes, remove all")

        composeRule.onNodeWithText("Nothing downloaded").performScrollTo().assertIsDisplayed()
        downloads.events shouldBe listOf("removeAll")
    }

    @Test
    fun `shows the space downloads take, and removing them all empties it`() {
        downloads.bytesUsed.value = 32_400_000L
        show()

        composeRule.onNodeWithText("Downloads use 32 MB").performScrollTo().assertIsDisplayed()
        clickText("Remove all downloads")
        clickText("Yes, remove all")

        composeRule.onNodeWithText("Nothing downloaded").performScrollTo().assertIsDisplayed()
        downloads.events shouldBe listOf("removeAll")
    }

    @Test
    fun `confirming sign-out signs out once, and the app is told once it's done`() {
        show()

        clickText("Sign out")
        clickText("Yes, sign out")
        composeRule.waitForIdle()
        // Asked again while still signing out: still the one sign-out
        composeRule.runOnIdle { viewModel.signOut() }
        signOuts shouldBe 1
        signedOut shouldBe 0

        signingOut.complete(Unit)
        composeRule.waitForIdle()

        signedOut shouldBe 1
    }

    @Test
    fun `a sign out that fails says so, and can be tried again`() {
        signOutFails = true
        show()
        clickText("Sign out")
        clickText("Yes, sign out")
        composeRule.waitForIdle()

        ShadowToast.getTextOfLatestToast() shouldBe context.getString(CommonR.string.sign_out_failed)
        signedOut shouldBe 0

        signOutFails = false
        signingOut.complete(Unit)
        clickText("Sign out")
        clickText("Yes, sign out")
        composeRule.waitForIdle()

        signOuts shouldBe 2
        signedOut shouldBe 1
    }

    @Test
    fun `back goes back`() {
        show()

        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.vault_back)).performClick()

        backs shouldBe 1
    }

    @Test
    fun `the settings screen stays clear of the status and navigation bars`() {
        show()
        composeRule.runOnIdle {
            val bars = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, STATUS_BAR_PX, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAV_BAR_PX))
                .build()
            ViewCompat.dispatchApplyWindowInsets(composeRule.activity.findViewById<View>(android.R.id.content), bars)
        }

        val title = composeRule.onNodeWithText("Settings").fetchSemanticsNode()
        val screenHeight = composeRule.onRoot().fetchSemanticsNode().size.height
        title.boundsInRoot.top shouldBeGreaterThanOrEqual STATUS_BAR_PX.toFloat()
        val lastRow = composeRule.onNodeWithText("Sign out").performScrollTo().fetchSemanticsNode()
        lastRow.boundsInRoot.bottom shouldBeLessThanOrEqual (screenHeight - NAV_BAR_PX).toFloat()
    }

    private companion object {
        const val STATUS_BAR_PX = 63
        const val NAV_BAR_PX = 126
    }
}

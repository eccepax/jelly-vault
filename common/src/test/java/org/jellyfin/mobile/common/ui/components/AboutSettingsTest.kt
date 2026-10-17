package org.jellyfin.mobile.common.ui.components

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AboutSettingsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the bug report link opens GitHub's bug form with the app, version and device filled in`() {
        val uri = Uri.parse(
            bugReportUrl(appName = "TuneVault", version = "1.2.3", device = "Google Pixel 10 Pro, Android 16"),
        )

        uri.toString() shouldStartWith "https://github.com/eccepax/jelly-vault/issues/new?"
        uri.getQueryParameter("template") shouldBe "bug-report.yaml"
        // The form's field ids, from .github/ISSUE_TEMPLATE/bug-report.yaml
        uri.getQueryParameter("app") shouldBe "TuneVault"
        uri.getQueryParameter("app-version") shouldBe "1.2.3"
        uri.getQueryParameter("device") shouldBe "Google Pixel 10 Pro, Android 16"
    }

    @Test
    fun `about shows the app and its version, and reporting a bug opens the form`() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides RecordingUriHandler(opened)) {
                AppTheme(VaultBrand.TuneVault) {
                    AboutSettings(appName = "TuneVault", version = "1.2.3", device = "Pixel, Android 16")
                }
            }
        }

        composeRule.onNodeWithText("TuneVault 1.2.3").assertExists()
        composeRule.onNodeWithText("Report a bug").performClick()

        opened shouldBe listOf(bugReportUrl("TuneVault", "1.2.3", "Pixel, Android 16"))
    }

    private class RecordingUriHandler(private val opened: MutableList<String>) : UriHandler {
        override fun openUri(uri: String) {
            opened += uri
        }
    }
}

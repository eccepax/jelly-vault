package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Library toolbar overflow -> Settings -> "Skip silence" switch. Verified via [koinGet] rather than
 * re-opening Settings, so the test checks what playback actually reads.
 */
@AccountArea
class SkipSilenceSettingE2ETest : LoggedInE2ETest() {

    @Test
    fun togglingSkipSilencePersistsThePreference() {
        loginAndWaitForLibrary()
        assertTrue(!koinGet<AudioVaultAppPreferences>().skipSilenceEnabled)

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        chooseFromLibraryMenu(context.getString(R.string.activity_name_settings))

        val skipSilenceTitle = context.getString(R.string.pref_skip_silence_title)
        waitForComposeText(skipSilenceTitle)
        composeRule.onNodeWithText(skipSilenceTitle).performScrollTo().performClick()

        assertTrue(koinGet<AudioVaultAppPreferences>().skipSilenceEnabled)
    }
}

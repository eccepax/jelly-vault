package org.jellyfin.mobile.common.account

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.common.app.AppPreferences
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadsOnSignOutTest {

    private val preferences = AppPreferences(ApplicationProvider.getApplicationContext())
    private var removed = false
    private val downloadsOnSignOut = DownloadsOnSignOut(preferences) { removed = true }

    @Test
    fun `downloads are kept when signing out, unless asked otherwise`() = runTest {
        preferences.removeDownloadsOnSignOut shouldBe false

        downloadsOnSignOut()

        removed shouldBe false
    }

    @Test
    fun `with the setting on, signing out removes the downloads`() = runTest {
        preferences.removeDownloadsOnSignOut = true

        downloadsOnSignOut()

        removed shouldBe true
    }

    @Test
    fun `the setting is remembered`() {
        preferences.removeDownloadsOnSignOut = true

        AppPreferences(ApplicationProvider.getApplicationContext()).removeDownloadsOnSignOut shouldBe true
    }
}

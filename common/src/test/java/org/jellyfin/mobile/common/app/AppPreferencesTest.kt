package org.jellyfin.mobile.common.app

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppPreferencesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val stored = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    @Test
    fun `settings that no longer exist are cleared from upgraded installs`() {
        stored.edit {
            putBoolean("pref_ignore_bluetooth_permission", true)
            putLong(AppPreferences.PREF_SERVER_ID, 7L)
        }

        val preferences = AppPreferences(context)

        stored.contains("pref_ignore_bluetooth_permission") shouldBe false
        preferences.currentServerId shouldBe 7L
    }
}

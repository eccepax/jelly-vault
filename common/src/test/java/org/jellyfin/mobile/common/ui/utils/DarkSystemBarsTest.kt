package org.jellyfin.mobile.common.ui.utils

import android.app.Application
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.core.view.WindowInsetsControllerCompat
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The apps are dark whatever the phone's theme, so the system bars must be too. With the phone in
 * light mode and 3-button navigation, the navigation bar used to get a light-gray scrim with dark
 * buttons under the dark app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "notnight")
class DarkSystemBarsTest {

    private fun darkActivity(): ComponentActivity =
        Robolectric.buildActivity(ComponentActivity::class.java).create().get().apply { enableDarkEdgeToEdge() }

    @Test
    fun `the navigation bar stays dark with light buttons, even with the phone in light mode`() {
        val activity = darkActivity()
        val bars = WindowInsetsControllerCompat(activity.window, activity.window.decorView)

        bars.isAppearanceLightNavigationBars shouldBe false
        Color.luminance(activity.window.navigationBarColor) shouldBeLessThan 0.5f
    }

    @Test
    fun `the status bar stays dark with light icons`() {
        val activity = darkActivity()
        val bars = WindowInsetsControllerCompat(activity.window, activity.window.decorView)

        bars.isAppearanceLightStatusBars shouldBe false
    }
}

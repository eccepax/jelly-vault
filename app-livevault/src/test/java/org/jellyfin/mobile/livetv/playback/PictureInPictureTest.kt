package org.jellyfin.mobile.livetv.playback

import android.app.Application
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Leaving the app (Home, or a gesture) while a channel plays shrinks the player into a floating
 * window. On Android 12+ the system does it from auto-enter params; before that, on the leave hint,
 * which is what these run against.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [30])
class PictureInPictureTest {

    private var playing by mutableStateOf(true)

    private fun launch(pipSupported: Boolean = true, tv: Boolean = false): ActivityController<ComponentActivity> {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, pipSupported)
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, tv)
        // A TV is told by its UI mode (see isTelevision)
        if (tv) org.robolectric.RuntimeEnvironment.setQualifiers("+television")
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent { PictureInPictureOnLeave(enabled = playing) }
        shadowOf(Looper.getMainLooper()).idle()
        return controller
    }

    @Test
    fun `leaving the app while a channel plays shrinks it into a window`() {
        val controller = launch()

        controller.userLeaving()

        controller.get().isInPictureInPictureMode shouldBe true
    }

    @Test
    fun `leaving when nothing is playing just leaves`() {
        playing = false
        val controller = launch()

        controller.userLeaving()

        controller.get().isInPictureInPictureMode shouldBe false
    }

    @Test
    fun `once playing stops, leaving just leaves`() {
        val controller = launch()
        playing = false
        shadowOf(Looper.getMainLooper()).idle()

        controller.userLeaving()

        controller.get().isInPictureInPictureMode shouldBe false
    }

    @Test
    fun `a device without picture-in-picture just leaves`() {
        val controller = launch(pipSupported = false)

        controller.userLeaving()

        controller.get().isInPictureInPictureMode shouldBe false
    }

    @Test
    fun `on a TV, Home leaves rather than keeping a tuner busy in a corner`() {
        val controller = launch(tv = true)

        controller.userLeaving()

        controller.get().isInPictureInPictureMode shouldBe false
    }

    @Test
    fun `the screen knows when it's in the window and when it's back`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var inWindow: Boolean? = null
        activity.setContent {
            val inPip = rememberInPictureInPicture()
            inWindow = inPip
            Text(inPip.toString())
        }
        shadowOf(Looper.getMainLooper()).idle()
        inWindow shouldBe false

        activity.onPictureInPictureModeChanged(true, Configuration(activity.resources.configuration))
        shadowOf(Looper.getMainLooper()).idle()
        inWindow shouldBe true

        activity.onPictureInPictureModeChanged(false, Configuration(activity.resources.configuration))
        shadowOf(Looper.getMainLooper()).idle()
        inWindow shouldBe false
    }
}

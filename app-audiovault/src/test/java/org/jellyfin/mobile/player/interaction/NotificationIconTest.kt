package org.jellyfin.mobile.player.interaction

import android.app.Application
import android.graphics.Bitmap
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Below Android 10 the notification carries the cover as its large icon, and the whole notification
 * travels to the playback service in an Intent. A full-size cover (~900 KB) overflowed Binder's
 * 1 MB limit, so the service never started in the foreground and Android 8 killed the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotificationIconTest {

    @Test
    fun `a large cover is scaled down to fit the icon size, keeping its aspect ratio`() {
        val cover = Bitmap.createBitmap(1000, 800, Bitmap.Config.ARGB_8888)

        val icon = cover.scaledToFit(maxPx = 200)

        icon.width shouldBe 200
        icon.height shouldBe 160
    }

    @Test
    fun `a tall cover is limited by its height`() {
        val cover = Bitmap.createBitmap(500, 1000, Bitmap.Config.ARGB_8888)

        val icon = cover.scaledToFit(maxPx = 200)

        icon.width shouldBe 100
        icon.height shouldBe 200
    }

    @Test
    fun `a cover that already fits is used as is`() {
        val cover = Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888)

        cover.scaledToFit(maxPx = 200) shouldBeSameInstanceAs cover
    }
}

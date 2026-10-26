package org.jellyfin.mobile.common.downloads

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VaultDownloadServiceTest {

    @Test
    fun `the downloads notification shows the app's own icon`() {
        val service = Robolectric.buildService(AppDownloadService::class.java).get()

        val notification = service.foregroundNotification()

        notification.smallIcon.resId shouldBe APP_ICON
    }

    /** As an app subclasses it, with its own icon. */
    class AppDownloadService : VaultDownloadService(android.R.string.ok, smallIcon = APP_ICON) {
        override fun getDownloadManager(): DownloadManager = error("Not downloading in this test")

        override fun progressTitle(active: Int) = "Downloading $active"

        fun foregroundNotification() = getForegroundNotification(mutableListOf(), 0)
    }

    private companion object {
        val APP_ICON = android.R.drawable.ic_media_play
    }
}

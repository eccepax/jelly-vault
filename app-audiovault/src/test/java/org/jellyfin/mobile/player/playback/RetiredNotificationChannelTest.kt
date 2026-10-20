package org.jellyfin.mobile.player.playback

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The channel AudioVault's own notification used, before Media3 took the notification over. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RetiredNotificationChannelTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val notifications = context.getSystemService<NotificationManager>()!!

    @Test
    fun `an upgraded install loses the old player channel from its notification settings`() {
        notifications.createNotificationChannel(
            NotificationChannel(RETIRED_PLAYER_CHANNEL, "AudioVault", NotificationManager.IMPORTANCE_LOW),
        )

        removeRetiredNotificationChannels(context)

        notifications.getNotificationChannel(RETIRED_PLAYER_CHANNEL) shouldBe null
    }

    @Test
    fun `other channels, like the downloads', stay`() {
        notifications.createNotificationChannel(
            NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_LOW),
        )

        removeRetiredNotificationChannels(context)

        notifications.getNotificationChannel("downloads")?.id shouldBe "downloads"
    }
}

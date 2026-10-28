package org.jellyfin.mobile.livetv.playback

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jellyfin.mobile.cast.Casting
import org.jellyfin.mobile.cast.NoCasting
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/** Keeping the app alive while a channel plays on a cast device, through a foreground service. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LiveCastServiceTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val keepAlive = ServiceCastKeepAlive(context)
    private var service: ServiceController<LiveCastService>? = null

    @After
    fun tearDown() {
        service?.destroy()
        CastHold.current = null
    }

    /** The service Android was asked to start, started as Android would. */
    private fun startService(): LiveCastService {
        val intent = shadowOf(context).nextStartedService
        intent shouldNotBe null
        val controller = Robolectric.buildService(LiveCastService::class.java, intent).create().startCommand(0, 1)
        service = controller
        return controller.get()
    }

    private val shownTitle: String?
        get() = shadowOf(context.getSystemService(NotificationManager::class.java))
            .getNotification(LiveCastService.NOTIFICATION_ID)
            ?.extras?.getString(Notification.EXTRA_TITLE)

    @Test
    fun `holding the app starts the service, which shows the channel`() {
        keepAlive.hold("4.1 Devils", "Main Bedroom TV")

        val started = startService()

        shadowOf(started).lastForegroundNotification.extras.getString(Notification.EXTRA_TITLE) shouldBe "4.1 Devils"
        LiveCastService.running shouldBe true
    }

    @Test
    fun `released before the service has started, it still goes to the foreground before stopping itself`() {
        // A channel that fails at once while casting: held, then released, before the service runs
        keepAlive.hold("4.1 Devils", "Main Bedroom TV")
        keepAlive.release()

        val started = startService()

        shadowOf(context).nextStoppedService.shouldBeNull()
        // Out of the foreground again: it was in it, as Android requires before it stops
        shadowOf(started).isForegroundStopped shouldBe true
        shadowOf(started).isStoppedBySelf shouldBe true
    }

    @Test
    fun `released while running, the service stops itself`() {
        keepAlive.hold("4.1 Devils", "Main Bedroom TV")
        val started = startService()

        keepAlive.release()

        shadowOf(started).isStoppedBySelf shouldBe true
        LiveCastService.running shouldBe false
    }

    @Test
    fun `another channel shows in the notification, without starting the service again`() {
        keepAlive.hold("4.1 Devils", "Main Bedroom TV")
        startService()

        keepAlive.hold("4.2 Blackhawks", "Main Bedroom TV")

        shadowOf(context).nextStartedService.shouldBeNull()
        shownTitle shouldBe "4.2 Blackhawks"
    }

    @Test
    fun `held again once stopped, the service starts again`() {
        keepAlive.hold("4.1 Devils", "Main Bedroom TV")
        startService()
        keepAlive.release()
        service?.destroy()
        service = null

        keepAlive.hold("4.2 Blackhawks", "Main Bedroom TV")

        shadowOf(context).nextStartedService shouldNotBe null
    }

    @Test
    fun `tapping the notification brings back the activity that's casting, rather than opening another`() {
        keepAlive.hold("4.1 Devils", "Main Bedroom TV")
        val started = startService()

        val tapped = shadowOf(shadowOf(started).lastForegroundNotification.contentIntent).savedIntent

        (tapped.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP) shouldNotBe 0
        (tapped.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP) shouldNotBe 0
    }

    @Test
    fun `Stop casting from a notification left over, with no service running, ends the cast and stops it again`() {
        var castsStopped = 0
        startKoin {
            modules(
                module {
                    single<Casting> {
                        object : Casting by NoCasting {
                            override fun stopCasting() {
                                castsStopped++
                            }
                        }
                    }
                },
            )
        }
        try {
            val stop = Intent(context, LiveCastService::class.java).setAction(LiveCastService.ACTION_STOP_CASTING)
            val controller = Robolectric.buildService(LiveCastService::class.java, stop).create().startCommand(0, 1)
            service = controller

            castsStopped shouldBe 1
            shadowOf(controller.get()).isStoppedBySelf shouldBe true
        } finally {
            stopKoin()
        }
    }
}

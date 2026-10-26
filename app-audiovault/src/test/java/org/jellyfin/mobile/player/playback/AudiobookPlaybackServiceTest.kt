package org.jellyfin.mobile.player.playback

import android.app.Application
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.cast.Casting
import org.jellyfin.mobile.cast.NoCasting
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.junit.After
import org.junit.Before
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AudiobookPlaybackServiceTest {

    private val preferences = mockk<AudioVaultAppPreferences>(relaxed = true) {
        every { playbackSpeed } returns 1.5f
        every { skipSilenceEnabled } returns true
    }
    private val appController = mockk<DeferredAudiobookController>(relaxed = true)
    private lateinit var controller: ServiceController<AudiobookPlaybackService>
    private val service get() = controller.get()

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<MediaSource.Factory> { FakeAudioSourceFactory(failing = setOf("unreachable")) }
                    single<ItemDownloads> { mockk { every { fileFor(any()) } returns null } }
                    single { preferences }
                    single { mockk<AudiobookVoiceSearch>() }
                    single { mockk<JellyfinAudiobooks>() }
                    single { appController }
                    single<Casting> { NoCasting }
                },
            )
        }
        controller = Robolectric.buildService(AudiobookPlaybackService::class.java).create()
    }

    @After
    fun tearDown() {
        controller.destroy()
        stopKoin()
    }

    private val player get() = service.onGetSession(mockk())!!.player
    private val exoPlayer get() = (player as ForwardingPlayer).wrappedPlayer as ExoPlayer

    @Test
    fun `next and previous move between chapters`() {
        player.shouldBeInstanceOf<AudiobookPlayer>()
    }

    @Test
    fun `the skip buttons go back 10 seconds and forward 30`() {
        player.seekBackIncrement shouldBe 10_000L
        player.seekForwardIncrement shouldBe 30_000L
    }

    @Test
    fun `it plays at the speed chosen last time, with silence skipped if that was on`() {
        player.playbackParameters.speed shouldBe 1.5f
        exoPlayer.skipSilenceEnabled shouldBe true
    }

    @Test
    fun `it plays as spoken word`() {
        player.audioAttributes.contentType shouldBe C.AUDIO_CONTENT_TYPE_SPEECH
    }

    @Test
    fun `it pauses when headphones are unplugged`() {
        player.playWhenReady = true
        // ExoPlayer starts listening for unplugging on its playback thread, not the main one
        advance(exoPlayer).untilPendingCommandsAreFullyHandled()

        service.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()

        player.playWhenReady shouldBe false
    }

    @Test
    fun `swiping the app away with nothing playing stops the service`() {
        service.onTaskRemoved(null)

        shadowOf(service).isStoppedBySelf shouldBe true
    }

    @Test
    fun `swiping the app away while a book plays leaves it playing`() {
        player.setMediaItem(
            AudiobookTrack(id = "a", title = "a", durationMs = 0L).toMediaItem { "https://vault.test/$it" },
        )
        player.prepare()
        player.play()

        service.onTaskRemoved(null)

        shadowOf(service).isStoppedBySelf shouldBe false
    }

    @Test
    fun `starting, it connects the app to it, so the app follows a book started outside it (by voice, say)`() {
        verify { appController.connect() }
    }

    @Test
    fun `swiping the app away while paused takes the paused book, and its notification, with it`() {
        val paused = player
        paused.setMediaItem(
            AudiobookTrack(id = "a", title = "a", durationMs = 0L).toMediaItem { "https://vault.test/$it" },
        )
        paused.prepare()

        service.onTaskRemoved(null)

        shadowOf(service).isStoppedBySelf shouldBe true
        player shouldNotBe paused
        player.mediaItemCount shouldBe 0
    }

    @Test
    fun `swiping the app away with the book stopped by an error takes it, and its notification, with it`() {
        val stopped = player
        stopped.setMediaItem(
            AudiobookTrack(id = "unreachable", title = "a", durationMs = 0L).toMediaItem { "https://vault.test/$it" },
        )
        stopped.prepare()
        stopped.play()
        advance(exoPlayer).untilPlayerError()

        service.onTaskRemoved(null)

        shadowOf(service).isStoppedBySelf shouldBe true
        player shouldNotBe stopped
    }

    @Test
    fun `swiping the app away with the book finished takes it, and its notification, with it`() {
        val finished = player
        finished.setMediaItem(
            AudiobookTrack(id = "a", title = "a", durationMs = 0L).toMediaItem { "https://vault.test/$it" },
        )
        finished.prepare()
        finished.play()
        finished.seekTo(99_900L)
        advance(exoPlayer).untilState(Player.STATE_ENDED)

        service.onTaskRemoved(null)

        shadowOf(service).isStoppedBySelf shouldBe true
        player shouldNotBe finished
    }
}

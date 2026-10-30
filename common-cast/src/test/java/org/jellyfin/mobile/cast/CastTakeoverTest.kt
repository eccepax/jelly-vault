package org.jellyfin.mobile.cast

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.DeviceInfo
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Another app taking the cast device over ends this app's cast, leaving the cast device to it: this
 * app shows, reports and brings back nothing of the other's.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
@Config(application = Application::class)
class CastTakeoverTest {

    private val exo = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
        .setMediaSourceFactory(FakeAudioSourceFactory())
        .build()

    /** The song the cast device plays (null: nothing), and whether playback is there at all. */
    private var playingThere: MediaItem? = null
    private var remote = true
    private val listeners = mutableListOf<Player.Listener>()

    private val castDevice = object : ForwardingPlayer(exo) {
        override fun getDeviceInfo(): DeviceInfo =
            DeviceInfo.Builder(if (remote) DeviceInfo.PLAYBACK_TYPE_REMOTE else DeviceInfo.PLAYBACK_TYPE_LOCAL).build()

        override fun getCurrentMediaItem(): MediaItem? = playingThere

        override fun addListener(listener: Player.Listener) {
            listeners += listener
            super.addListener(listener)
        }
    }

    private val sent = SentItems()
    private var left = 0

    init {
        CastTakeover(castDevice, sent) { left++ }
    }

    private fun song(id: String) = MediaItem.Builder().setMediaId(id).setUri("https://vault.test/$id").build()

    /** The cast device's status changes to [song] playing there. */
    private fun castDevicePlays(song: MediaItem?) {
        playingThere = song
        val events = Player.Events(FlagSet.Builder().add(Player.EVENT_MEDIA_ITEM_TRANSITION).build())
        listeners.toList().forEach { it.onEvents(castDevice, events) }
    }

    @After
    fun tearDown() {
        exo.release()
    }

    @Test
    fun `another app's song replacing this app's own there ends this app's cast`() {
        sent.remember(song("mine"))
        castDevicePlays(song("mine"))

        castDevicePlays(song("theirs"))

        left shouldBe 1
    }

    @Test
    fun `this app's own songs playing on there, and the queue moving between them, keep the cast`() {
        listOf("a", "b").forEach { sent.remember(song(it)) }

        castDevicePlays(song("a"))
        castDevicePlays(song("b"))
        castDevicePlays(null)

        left shouldBe 0
    }

    @Test
    fun `joining a cast device already playing another app's song doesn't end the cast`() {
        // This app's own queue may be on its way there: it hasn't had a song of its own there yet
        castDevicePlays(song("theirs"))

        left shouldBe 0
    }

    @Test
    fun `taken over again after casting anew, the cast ends again`() {
        sent.remember(song("mine"))
        castDevicePlays(song("mine"))
        castDevicePlays(song("theirs"))
        castDevicePlays(song("mine"))

        castDevicePlays(song("theirs"))

        left shouldBe 2
    }

    @Test
    fun `played here, nothing is ended`() {
        remote = false
        sent.remember(song("mine"))
        castDevicePlays(song("mine"))

        castDevicePlays(song("theirs"))

        left shouldBe 0
    }
}

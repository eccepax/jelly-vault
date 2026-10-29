package org.jellyfin.mobile.livetv.playback

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.livetv.catalog.channel
import org.jellyfin.mobile.livetv.domain.LiveProgram
import org.junit.jupiter.api.Test
import java.time.Instant

/** Playing a channel here, or on the cast device while one is connected. */
class CastingLivePlayerTest {

    private val log = EventLog()
    private val local = FakeLivePlayer(log)
    private val cast = FakeLiveCasting(log)
    private val devils = channel(
        "4.1",
        "Devils",
        now = LiveProgram("Devils at Rangers", Instant.EPOCH, Instant.EPOCH),
        logoUrl = "https://vault.test/logo.png",
    )
    private val player = CastingLivePlayer(local, cast, describe = { if (it == devils.id) devils else null })

    @Test
    fun `with no cast device a channel plays here`() {
        player.play("http://vault/a.m3u8", devils.id)

        log.events shouldContainExactly listOf("player.play:http://vault/a.m3u8")
    }

    @Test
    fun `a stream opened for here plays here, though a cast device connected while it opened`() {
        cast.connected.value = true

        player.play("http://vault/a.ts", devils.id, forCast = false)

        log.events shouldContainExactly listOf("player.play:http://vault/a.ts")
    }

    @Test
    fun `a stream opened for the cast device never plays here, though the cast ended while it opened`() {
        player.play("https://vault/a/master.m3u8?ApiKey=t", devils.id, forCast = true)

        log.events shouldContainExactly listOf("cast.play:https://vault/a/master.m3u8?ApiKey=t")
    }

    @Test
    fun `with a cast device connected a channel plays there, as HLS`() {
        cast.connected.value = true

        player.play("https://vault/a/master.m3u8?ApiKey=t", devils.id)

        log.events shouldContainExactly listOf("cast.play:https://vault/a/master.m3u8?ApiKey=t")
        cast.sent!![1] shouldBe "application/x-mpegURL"
    }

    @Test
    fun `the cast device shows the channel and its logo, but not what's on, which would go stale`() {
        cast.connected.value = true

        player.play("https://vault/a/master.m3u8", devils.id)

        cast.sent!!.drop(2) shouldBe listOf("4.1 Devils", null, "https://vault.test/logo.png")
    }

    @Test
    fun `a channel it knows nothing about shows as its id`() {
        cast.connected.value = true

        player.play("https://vault/a/master.m3u8", "unknown")

        cast.sent!!.drop(2) shouldBe listOf("unknown", null, null)
    }

    @Test
    fun `the player is on the cast device while one is connected`() {
        player.onCastDevice.value shouldBe false

        cast.connected.value = true

        player.onCastDevice.value shouldBe true
    }

    @Test
    fun `stopping stops where the channel plays`() {
        cast.connected.value = true
        player.play("https://vault/a/master.m3u8", devils.id)
        log.events.clear()

        player.stop()

        log.events shouldContainExactly listOf("cast.stop")
    }

    @Test
    fun `stopping after a cast device connected mid-channel stops it here`() {
        player.play("http://vault/a.m3u8", devils.id)
        cast.connected.value = true
        log.events.clear()

        player.stop()

        log.events shouldContainExactly listOf("player.stop")
    }

    @Test
    fun `the video has started when it has where the channel plays`() = runTest {
        cast.connected.value = true
        player.play("https://vault/a/master.m3u8", devils.id)
        local.videoStarted.value = true
        player.videoStarted.first() shouldBe false

        cast.started.value = true

        player.videoStarted.first() shouldBe true
    }

    @Test
    fun `an error on the cast device is passed on while the channel plays there`() {
        var errors = 0
        player.listenWith { errors++ }
        cast.connected.value = true
        player.play("https://vault/a/master.m3u8", devils.id)

        cast.onError!!()

        errors shouldBe 1
    }

    @Test
    fun `an error here is passed on while the channel plays here`() {
        var errors = 0
        player.listenWith { errors++ }
        player.play("http://vault/a.m3u8", devils.id)

        local.listener!!.onError()

        errors shouldBe 1
    }

    @Test
    fun `a late error from where the channel no longer plays is ignored`() {
        var errors = 0
        player.listenWith { errors++ }
        player.play("http://vault/a.m3u8", devils.id)
        cast.connected.value = true
        player.play("https://vault/a/master.m3u8", devils.id)

        local.listener!!.onError()

        errors shouldBe 0
    }

    @Test
    fun `releasing releases both players`() {
        player.release()

        local.released shouldBe true
        cast.released shouldBe true
    }

    @Test
    fun `the channel stopped on the cast device itself is passed on`() {
        var stopped = 0
        player.listenWith(
            object : LivePlayer.Listener {
                override fun onError() = Unit
                override fun onStoppedElsewhere() {
                    stopped++
                }
            },
        )
        cast.connected.value = true
        player.play("https://vault/a/master.m3u8", devils.id)

        cast.onStopped!!()

        stopped shouldBe 1
    }
}

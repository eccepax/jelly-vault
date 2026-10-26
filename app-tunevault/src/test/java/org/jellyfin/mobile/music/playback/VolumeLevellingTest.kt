package org.jellyfin.mobile.music.playback

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.music.domain.PlayQueue
import org.jellyfin.mobile.music.domain.track
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeLevellingTest {

    private val playback = FakePlaybackController()
    private val volume = PlaybackVolume(playback)
    private val enabled = MutableStateFlow(true)

    private val loud = track("loud").copy(normalizationGainDb = -6.02f)
    private val quiet = track("quiet").copy(normalizationGainDb = 4f)
    private val unmeasured = track("unmeasured").copy(normalizationGainDb = null)

    private fun TestScope.levelling() {
        VolumeLeveller(playback, volume, enabled, backgroundScope).start()
        runCurrent()
    }

    private fun TestScope.playing(vararg tracks: org.jellyfin.mobile.music.domain.Track, index: Int = 0) {
        playback.state.value = playback.state.value.copy(queue = PlayQueue.of(tracks.toList(), index), isPlaying = true)
        runCurrent()
    }

    @Test
    fun `a gain in decibels becomes a volume`() {
        levelFor(-6.02f) shouldBe (0.5f plusOrMinus 0.001f)
        levelFor(-20f) shouldBe (0.1f plusOrMinus 0.001f)
        levelFor(0f) shouldBe 1f
    }

    @Test
    fun `quiet songs aren't pushed past full volume, which would distort`() {
        levelFor(4f) shouldBe 1f
    }

    @Test
    fun `a song the server hasn't measured plays as it is`() {
        levelFor(null) shouldBe 1f
    }

    @Test
    fun `a loud song is turned down, and the next one set for itself`() = runTest {
        levelling()

        playing(loud, quiet)
        playback.volumes.last() shouldBe (0.5f plusOrMinus 0.001f)

        playing(loud, quiet, index = 1)
        playback.volumes.last() shouldBe 1f
    }

    @Test
    fun `turned off, every song plays as it is`() = runTest {
        levelling()
        playing(loud)

        enabled.value = false
        runCurrent()

        playback.volumes.last() shouldBe 1f
    }

    @Test
    fun `turned back on, it applies to the playing song at once`() = runTest {
        enabled.value = false
        levelling()
        playing(loud)

        enabled.value = true
        runCurrent()

        playback.volumes.last() shouldBe (0.5f plusOrMinus 0.001f)
    }

    @Test
    fun `an unmeasured song after a loud one goes back to full`() = runTest {
        levelling()
        playing(loud, unmeasured)

        playing(loud, unmeasured, index = 1)

        playback.volumes.last() shouldBe 1f
    }

    @Test
    fun `the sleep timer's fade works from the levelled volume, not over it`() = runTest {
        levelling()
        playing(loud)

        volume.setFade(0.5f)
        playback.volumes.last() shouldBe (0.25f plusOrMinus 0.001f)

        volume.setFade(1f)
        playback.volumes.last() shouldBe (0.5f plusOrMinus 0.001f)
    }

    @Test
    fun `nothing is sent when the volume doesn't change`() = runTest {
        levelling()
        playing(unmeasured)

        playback.volumes shouldBe emptyList()
    }

    private fun TestScope.casting(remote: Boolean) {
        playback.state.value = playback.state.value.copy(isRemote = remote)
        runCurrent()
    }

    @Test
    fun `while casting, the cast device's volume is left alone`() = runTest {
        levelling()
        casting(true)

        playing(loud)
        volume.setFade(0.5f)

        playback.volumes shouldBe emptyList()
    }

    @Test
    fun `back from casting, the song playing here is levelled for itself`() = runTest {
        levelling()
        playing(loud, quiet)
        casting(true)
        playing(loud, quiet, index = 1)
        playback.volumes.size shouldBe 1

        casting(false)

        playback.volumes.last() shouldBe 1f
    }

    @Test
    fun `back from casting, this device's volume is set again only if it changed`() = runTest {
        levelling()
        playing(loud)
        casting(true)

        casting(false)

        playback.volumes.size shouldBe 1
    }
}

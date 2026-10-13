package org.jellyfin.mobile.music.e2e

import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.music.catalog.MusicCatalog
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.music.playback.VolumeLevellingSetting
import org.jellyfin.mobile.music.playback.levelFor
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PLAYBACK_TIMEOUT_MS = 30_000L

/** Volume levelling against real playback: the player's volume follows each song's measured gain. */
@PlaybackArea
class VolumeLevellingE2ETest : LoggedInE2ETest() {

    private val playback: PlaybackController get() = koinGet()
    private val setting: VolumeLevellingSetting get() = koinGet()

    @After
    fun levellingOn() = onMain { setting.setOn(true) }

    private fun volumeIs(expected: Float) = onMain { abs(playback.volume - expected) < 0.01f }

    @Test
    fun aLoudSongIsTurnedDownAndSwitchingLevellingOffRestoresIt() {
        connectAndLoginToLibrary()
        val tracks = runBlocking {
            val catalog: MusicCatalog = koinGet()
            catalog.albumTracks(catalog.albums().first { it.title == "The Purple Bird" }.id)
        }
        val loudest = tracks.minBy { it.normalizationGainDb ?: 0f }
        val expected = levelFor(loudest.normalizationGainDb)
        assertTrue("needs a song the server measured as loud: $loudest", expected < 0.95f)
        openAlbum("The Purple Bird", firstTrack = "Turned To Dust (Rolling On)")

        scrollToAndClick(loudest.title)
        composeRule.waitUntil(PLAYBACK_TIMEOUT_MS) { onMain { playback.state.value.isPlaying } }

        composeRule.waitUntil(WAIT_TIMEOUT_MS) { volumeIs(expected) }

        onMain { setting.setOn(false) }
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { volumeIs(1f) }
    }
}

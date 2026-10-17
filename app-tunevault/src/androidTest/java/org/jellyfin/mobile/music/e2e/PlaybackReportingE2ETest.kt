package org.jellyfin.mobile.music.e2e

import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.music.playback.PlaybackReportingTracker
import org.jellyfin.mobile.testing.VaultFixtures.PatternSuite
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Real reporting: listens past the threshold (half the track; seeking doesn't count, so this takes
 * ~100 s of real playback) and checks the Vault itself saw it. Restores the track's play state.
 */
@PlaybackArea
class PlaybackReportingE2ETest : LoggedInE2ETest() {

    private val playback: PlaybackController get() = koinGet()
    private val apiClient: ApiClient get() = koinGet()

    private var trackId: UUID? = null
    private var original: UserItemDataDto? = null

    @After
    fun restorePlayState() = runBlocking {
        val id = trackId ?: return@runBlocking
        apiClient.playStateApi.markUnplayedItem(id)
        if (original?.played == true) apiClient.playStateApi.markPlayedItem(id)
    }

    private fun userData(id: UUID): UserItemDataDto = runBlocking {
        apiClient.itemsApi.getItems(ids = listOf(id)).content.items.single().userData!!
    }

    @Test
    fun aTrackListenedPastHalfwayIsReportedAndCountedAsPlayed() {
        connectAndLoginToLibrary()
        openAlbum(PatternSuite.TITLE, firstTrack = PatternSuite.FIRST_TRACK)
        scrollToAndClick(PatternSuite.KNOWN_SONG)

        val track = waitForCurrentTrack()
        val id = track.id.toUUID()
        trackId = id
        original = userData(id)
        runBlocking { apiClient.playStateApi.markUnplayedItem(id) }

        val thresholdMs = minOf(track.durationMs / 2, PlaybackReportingTracker.MAX_THRESHOLD_MS)
        composeRule.waitUntil(thresholdMs + 60_000L) {
            runBlocking { apiClient.sessionApi.getSessions().content }
                .any { it.client == "TuneVault" && it.nowPlayingItem?.id == id }
        }

        onMain { playback.stop() }
        composeRule.waitUntil(30_000L) { userData(id).played }
        assertTrue(userData(id).played)
    }

    private fun waitForCurrentTrack() = run {
        composeRule.waitUntil(30_000L) { onMain { playback.state.value.currentTrack != null } }
        onMain { playback.state.value.currentTrack!! }
    }
}

package org.jellyfin.mobile.music.library

import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.domain.track
import org.jellyfin.mobile.music.downloads.FakeCollectionDownloads
import org.jellyfin.mobile.music.playback.FakePlaybackController
import org.junit.jupiter.api.Test

class TrackActionsViewModelTest {

    private val playback = FakePlaybackController()
    private val downloads = FakeCollectionDownloads()
    private val actions = TrackActionsViewModel(playback, downloads)

    @Test
    fun `play next queues the track after the playing one`() {
        actions.playNext(track("a"))

        playback.commands shouldBe listOf("playNext a")
    }

    @Test
    fun `add to queue queues it at the end`() {
        actions.addToQueue(track("a"))

        playback.commands shouldBe listOf("addToQueue a")
    }
}

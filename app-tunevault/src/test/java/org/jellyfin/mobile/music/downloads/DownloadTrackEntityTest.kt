package org.jellyfin.mobile.music.downloads

import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.domain.track
import org.junit.jupiter.api.Test

/** A downloaded song's details, kept for browsing and playing with no connection. */
class DownloadTrackEntityTest {

    @Test
    fun `a downloaded song keeps its container, so casting it later picks the same format as from the server`() {
        val song = track("a").copy(container = "flac", normalizationGainDb = -3f)

        DownloadTrackEntity.of(song).toTrack() shouldBe song
    }
}

package org.jellyfin.mobile.music.account

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.server.ServerSession
import org.jellyfin.mobile.music.catalog.FakeFavoritesStore
import org.jellyfin.mobile.music.catalog.FavoritesRepository
import org.jellyfin.mobile.music.domain.PlayQueue
import org.jellyfin.mobile.music.domain.track
import org.jellyfin.mobile.music.playback.FakePlaybackController
import org.jellyfin.mobile.music.playback.FakePlaybackReporter
import org.jellyfin.mobile.music.playback.PlaybackReportingTracker
import org.jellyfin.mobile.music.playback.PlaybackState
import org.junit.jupiter.api.Test

/** TuneVault's own sign-out steps; the shared sequence is covered by common's SignOutTest. */
@OptIn(ExperimentalCoroutinesApi::class)
class TuneVaultSignOutTest {

    private val events = mutableListOf<String>()
    private val playback = FakePlaybackController()
    private val reporter = FakePlaybackReporter()
    private val favorites = FavoritesRepository(FakeFavoritesStore(), TestScope(UnconfinedTestDispatcher()))
    private var session = ServerSession { events += "end session" }

    private fun TestScope.signOut(): SignOut {
        val tracker = PlaybackReportingTracker(playback, reporter, backgroundScope).also { it.start() }
        runCurrent()
        return tuneVaultSignOut(
            playback,
            tracker,
            favorites,
            session = { session.end() },
            clearAccountData = { events += "clear account data" },
            logout = { events += "logout" },
            stopCasting = { events += "stop casting after ${playback.commands}" },
        )
    }

    /** A song that has played long enough to have been reported to the server. */
    private fun TestScope.playingAReportedSong() {
        val song = track("a").copy(durationMs = 60_000L)
        playback.state.value = PlaybackState(queue = PlayQueue.of(listOf(song)), isPlaying = true)
        runCurrent()
        testScheduler.advanceTimeBy(31_000L)
        runCurrent()
    }

    @Test
    fun `ends any cast once playback has stopped, since the cast device holds stream urls with the token in them`() = runTest {
        val signOut = signOut()
        playingAReportedSong()

        signOut()

        events.filter { it.startsWith("stop casting") } shouldBe listOf("stop casting after [stop]")
        events.indexOfFirst { it.startsWith("stop casting") } shouldBe 0
    }

    @Test
    fun `stops playback, reports the stop, ends the session, then logs out`() = runTest {
        val signOut = signOut()
        playingAReportedSong()

        signOut()

        playback.commands shouldBe listOf("stop")
        reporter.reports.last() shouldBe "stopped a @0s"
        events shouldBe listOf("stop casting after [stop]", "end session", "clear account data", "logout")
    }

    @Test
    fun `forgets unsaved favorite changes`() = runTest {
        val signOut = signOut()
        favorites.setFavorite("a", true)

        signOut()

        favorites.isFavorite("a", serverValue = false) shouldBe false
    }
}

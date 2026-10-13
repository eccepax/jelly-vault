package org.jellyfin.mobile.music.playback

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.music.data.TuneDatabase
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ConnectException
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OfflinePlayReporterTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        TuneDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val server = FakePlaybackReporter()
    private var clock = Instant.parse("2026-10-04T12:00:00Z")
    private val reporter = OfflinePlayReporter(server, database.pendingPlayDao, now = { clock })

    @After
    fun tearDown() = database.close()

    private suspend fun kept() = database.pendingPlayDao.all().map {
        "${it.trackId} @ ${Instant.ofEpochMilli(it.playedAtMs)}"
    }

    @Test
    fun `reports go to the server while it answers, and nothing is kept`() = runTest {
        reporter.started("a", 0L, isPaused = false)

        server.reports shouldBe listOf("started a @0s")
        kept().shouldBeEmpty()
    }

    @Test
    fun `a play the server can't hear is kept, with when it happened`() = runTest {
        server.failure = ConnectException("refused")

        reporter.started("a", 0L, isPaused = false)

        kept() shouldBe listOf("a @ 2026-10-04T12:00:00Z")
    }

    @Test
    fun `progress and stops that can't be sent are let go`() = runTest {
        server.failure = ConnectException("refused")

        reporter.progress("a", 10_000L, isPaused = false)
        reporter.stopped("a", 20_000L)

        kept().shouldBeEmpty()
    }

    @Test
    fun `a server error isn't kept to resend`() = runTest {
        server.failure = InvalidStatusException(500)

        reporter.started("a", 0L, isPaused = false)

        kept().shouldBeEmpty()
    }

    @Test
    fun `syncing sends each kept play with its time, oldest first, then forgets them`() = runTest {
        server.failure = ConnectException("refused")
        reporter.started("a", 0L, isPaused = false)
        clock = clock.plusSeconds(300)
        reporter.started("b", 0L, isPaused = false)
        server.failure = null

        reporter.sync()

        server.reports shouldBe listOf("playedAt a 2026-10-04T12:00:00Z", "playedAt b 2026-10-04T12:05:00Z")
        kept().shouldBeEmpty()
    }

    @Test
    fun `syncing stops at a network failure and keeps the rest`() = runTest {
        server.failure = ConnectException("refused")
        reporter.started("a", 0L, isPaused = false)
        reporter.started("b", 0L, isPaused = false)

        reporter.sync()

        kept().size shouldBe 2
    }

    @Test
    fun `clearing forgets kept plays`() = runTest {
        server.failure = ConnectException("refused")
        reporter.started("a", 0L, isPaused = false)

        reporter.clear()

        kept().shouldBeEmpty()
    }
}

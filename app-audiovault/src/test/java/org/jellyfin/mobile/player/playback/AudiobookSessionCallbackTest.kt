package org.jellyfin.mobile.player.playback

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.ExecutionException

/** What the service does with requests that aren't plain player commands. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AudiobookSessionCallbackTest {

    private val exoPlayer: ExoPlayer = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
        .setMediaSourceFactory(FakeAudioSourceFactory())
        .build()
    private val session = mockk<MediaSession> { every { player } returns exoPlayer }
    private val searches = mutableListOf<String>()
    private val found = AudiobookQueue(
        tracks = listOf(AudiobookTrack(id = "found", title = "Found", durationMs = 60_000L)),
        startIndex = 0,
        startPositionMs = 12_000L,
    )
    private var match: AudiobookQueue? = found

    private val callback = AudiobookSessionCallback(
        exoPlayer = exoPlayer,
        findBook = { query ->
            searches += query
            match
        },
        streamUrl = { "https://vault.test/Audio/$it/universal" },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    )

    @After
    fun tearDown() {
        exoPlayer.release()
    }

    private fun searchFor(query: String) = callback.onSetMediaItems(
        session,
        mockk(),
        mutableListOf(
            MediaItem.Builder().setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setSearchQuery(query).build(),
            ).build(),
        ),
        0,
        0L,
    ).also { shadowOf(Looper.getMainLooper()).idle() }

    @Test
    fun `asking for a book by voice plays the best match, from where it was left`() {
        val result = searchFor("the found book").get()

        searches shouldBe listOf("the found book")
        result.mediaItems.map { it.mediaId } shouldBe listOf("found")
        result.mediaItems.single().localConfiguration?.uri.toString() shouldBe "https://vault.test/Audio/found/universal"
        result.startPositionMs shouldBe 12_000L
    }

    @Test
    fun `asking to play without saying what carries on with the book already loaded`() {
        exoPlayer.setMediaItems(
            listOf(
                AudiobookTrack(id = "a", title = "a", durationMs = 0L),
                AudiobookTrack(id = "b", title = "b", durationMs = 0L),
            )
                .map { it.toMediaItem { id -> "https://vault.test/$id" } },
            1,
            30_000L,
        )

        val result = searchFor("  ").get()

        searches shouldBe emptyList()
        result.mediaItems.map { it.mediaId } shouldBe listOf("a", "b")
        result.startIndex shouldBe 1
        result.startPositionMs shouldBe 30_000L
    }

    @Test
    fun `a search with no match plays nothing`() {
        match = null

        val result = searchFor("nonexistent")

        runCatching { result.get() }.exceptionOrNull()?.javaClass shouldBe ExecutionException::class.java
    }

    @Test
    fun `the app can turn skipping silence on and off`() {
        val result = callback.onCustomCommand(
            session,
            mockk(),
            AudiobookSessionCallback.SET_SKIP_SILENCE,
            Bundle().apply { putBoolean(AudiobookSessionCallback.KEY_ENABLED, true) },
        )

        result.get().resultCode shouldBe SessionResult.RESULT_SUCCESS
        exoPlayer.skipSilenceEnabled shouldBe true
    }

    @Test
    fun `items from a controller get their streams back`() {
        val sent = AudiobookTrack(id = "a", title = "a", durationMs = 0L).toMediaItem { "https://vault.test/$it" }
        val stripped = MediaItem.Builder().setMediaId("a").setRequestMetadata(sent.requestMetadata).build()

        val result = callback.onAddMediaItems(session, mockk(), mutableListOf(stripped)).get()

        result.single().localConfiguration?.uri.toString() shouldBe "https://vault.test/a"
    }

    @Test
    fun `the app's controller may skip silence, as well as the usual player commands`() {
        val connection = callback.onConnect(mockk(relaxed = true), mockk(relaxed = true))

        connection.availableSessionCommands.contains(AudiobookSessionCallback.SET_SKIP_SILENCE) shouldBe true
        connection.availablePlayerCommands.contains(Player.COMMAND_PLAY_PAUSE) shouldBe true
    }
}

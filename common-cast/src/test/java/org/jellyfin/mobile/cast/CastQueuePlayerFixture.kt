package org.jellyfin.mobile.cast

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.kotest.matchers.longs.shouldBeInRange
import org.junit.After
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * The queue owned here, with a window of it on the cast device. The cast device is a
 * [FakeCastDevice], which takes each command a round trip later and plays as time goes on; songs
 * last [SONG_MS], and those in [transcodes] are cast as a stream that starts where it's asked for.
 */
@OptIn(UnstableApi::class)
internal abstract class CastQueuePlayerFixture {

    protected val device = FakeCastDevice()
    protected var transcodes = setOf<String>()

    /** The streams asked for: the song and from where. */
    protected val asked = mutableListOf<Pair<String, Long>>()

    protected fun stream(item: MediaItem, fromMs: Long): CastStream {
        asked += item.mediaId to fromMs
        return if (item.mediaId in transcodes) {
            CastStream(
                "https://vault.test/${item.mediaId}?from=$fromMs",
                "audio/mpeg",
                startMs = fromMs,
                seekable = false,
            )
        } else {
            CastStream("https://vault.test/${item.mediaId}", "audio/flac")
        }
    }

    /** Each song's source, found at once. */
    protected val sources = CastSourceFinder { item, found -> found(CastSource { stream(item, it) }) }

    protected val player = CastQueuePlayer(device, sources)

    init {
        device.connect()
    }

    @After
    fun tearDown() {
        player.release()
    }

    protected fun item(id: String, durationMs: Long = SONG_MS) = MediaItem.Builder()
        .setMediaId(id)
        .setUri("https://vault.test/local/$id")
        .setMediaMetadata(MediaMetadata.Builder().setTitle("Song $id").setDurationMs(durationMs).build())
        .build()

    protected fun items(vararg ids: String) = ids.map { item(it) }

    protected fun settle(ms: Long = SETTLE_MS) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    protected fun playing(vararg ids: String, index: Int = 0, positionMs: Long = 0L, play: Boolean = true) {
        player.setMediaItems(items(*ids), index, positionMs)
        player.playWhenReady = play
        player.prepare()
        settle()
    }

    protected val Player.ids get() = (0 until mediaItemCount).map { getMediaItemAt(it).mediaId }

    /** The songs on the cast device, by their ids here. */
    protected val onDevice get() = device.queue.map { it.item.mediaId }

    protected fun Long.near(expectedMs: Long) = shouldBeInRange(expectedMs - SLACK_MS..expectedMs + SLACK_MS)

    protected companion object {
        const val SONG_MS = 100_000L

        /** Time enough for the commands a change takes, each a round trip. */
        const val SETTLE_MS = 1_000L
        const val SLACK_MS = 1_000L
    }
}

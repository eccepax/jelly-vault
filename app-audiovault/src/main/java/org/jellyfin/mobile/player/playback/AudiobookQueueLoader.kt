package org.jellyfin.mobile.player.playback

import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID

/** A book ready to play: its tracks, and where to start. */
data class AudiobookQueue(val tracks: List<AudiobookTrack>, val startIndex: Int, val startPositionMs: Long)

/**
 * Turns what the library or the downloads ask to play into tracks. From the downloads, a track
 * plays from the device; from the library it streams, so the server hears about it as it plays.
 * With the server out of reach, downloaded tracks play from the device instead, and the rest of
 * the book is left out.
 */
class AudiobookQueueLoader(
    /** The items with these ids, as the server has them (with chapters and where they were left). */
    private val items: suspend (ids: List<UUID>) -> List<BaseItemDto>,
    /** The track's download, if it's on the device. */
    private val playable: suspend (itemId: UUID) -> LocalJellyfinMediaSource?,
    private val coverUrl: (BaseItemDto) -> String?,
) {
    suspend fun load(options: PlayOptions): Result<AudiobookQueue> {
        val ids = options.ids.ifEmpty { listOfNotNull(options.mediaSourceId?.toUUIDOrNull()) }
        val startId = ids.getOrNull(options.startIndex) ?: return Result.failure(PlayerException.InvalidPlayOptions())
        val fromDownloads = options.playFromDownloads == true

        val downloads = ids.associateWith { playable(it) }
        val onServer = if (fromDownloads && downloads.values.all { it != null }) {
            emptyMap()
        } else {
            serverItems(ids)
        }

        val sources = ids.mapIndexedNotNull { i, id ->
            val download = downloads[id]
            val serverItem = onServer[id]
            val name = options.trackNames.getOrNull(i)
            when {
                fromDownloads && download != null -> Source(id, download.toTrack(name), download.resumeMs)
                serverItem != null -> Source(id, serverItem.toTrack(name, fromDevice = false), serverItem.resumeMs)
                download != null -> Source(id, download.toTrack(name), download.resumeMs)
                else -> null
            }
        }

        val startIndex = sources.indexOfFirst { it.id == startId }
        if (startIndex < 0) return Result.failure(PlayerException.NetworkFailure())
        val startPositionMs = options.startPosition?.inWholeMilliseconds ?: sources[startIndex].resumeMs
        return Result.success(AudiobookQueue(sources.map { it.track }, startIndex, startPositionMs))
    }

    private suspend fun serverItems(ids: List<UUID>): Map<UUID, BaseItemDto> = try {
        items(ids).associateBy { it.id }
    } catch (e: ApiClientException) {
        Timber.w(e, "Couldn't load the book from the server")
        emptyMap()
    }

    private class Source(val id: UUID, val track: AudiobookTrack, val resumeMs: Long)

    private fun BaseItemDto.toTrack(fallbackName: String?, fromDevice: Boolean) = AudiobookTrack(
        id = id.toString(),
        title = name ?: fallbackName.orEmpty(),
        bookTitle = album ?: seriesName,
        author = albumArtist ?: artists?.firstOrNull(),
        coverUrl = coverUrl(this),
        durationMs = runTimeTicks?.ticks?.inWholeMilliseconds ?: 0L,
        chapters = chapters.orEmpty().map { Chapter(it.startPositionTicks.ticks.inWholeMilliseconds, it.name) },
        fromDevice = fromDevice,
    )

    private fun LocalJellyfinMediaSource.toTrack(fallbackName: String?): AudiobookTrack {
        val track = item?.toTrack(fallbackName, fromDevice = true)
        val durationMs = runTime.inWholeMilliseconds
        return track?.copy(durationMs = track.durationMs.takeIf { it > 0 } ?: durationMs)
            ?: AudiobookTrack(id = itemId.toString(), title = fallbackName.orEmpty(), durationMs = durationMs, fromDevice = true)
    }

    private val BaseItemDto.resumeMs: Long
        get() = userData?.playbackPositionTicks?.ticks?.inWholeMilliseconds?.coerceAtLeast(0L) ?: 0L

    private val LocalJellyfinMediaSource.resumeMs: Long get() = startTime.inWholeMilliseconds
}

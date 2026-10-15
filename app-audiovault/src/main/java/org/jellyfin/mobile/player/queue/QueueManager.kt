package org.jellyfin.mobile.player.queue

import android.net.Uri
import androidx.annotation.CheckResult
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import org.jellyfin.mobile.downloads.AudiobookDownloads
import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.mobile.player.PlayerHost
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.source.ExternalSubtitleStream
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.player.source.RemoteJellyfinMediaSource
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.io.File
import java.util.UUID
import kotlin.time.Duration

/**
 * Suppresses TooManyFunctions: owns the whole playback-queue lifecycle (build/advance/persist
 * progress, resolve media sources for every play method). Splitting it apart for a lint count
 * would scatter closely coupled queue state across multiple classes.
 */
@Suppress("TooManyFunctions")
class QueueManager(
    private val viewModel: PlayerHost,
) : KoinComponent {
    private val apiClient: ApiClient = get()
    private val mediaSourceResolver: MediaSourceResolver by inject()
    private val downloads: AudiobookDownloads by inject()
    private val deviceProfileBuilder: DeviceProfileBuilder by inject()
    private val deviceProfile = deviceProfileBuilder.getDeviceProfile()

    private var currentQueue: List<UUID> = emptyList()
    private var currentQueueIndex: Int = 0
    private var currentTrackNames: List<String> = emptyList()

    val queueSize: Int get() = currentQueue.size
    val currentIndex: Int get() = currentQueueIndex
    fun getTrackNames(): List<String> = currentTrackNames

    private val _currentMediaSource: MutableLiveData<JellyfinMediaSource> = MutableLiveData()
    val currentMediaSource: LiveData<JellyfinMediaSource>
        get() = _currentMediaSource

    fun getCurrentMediaSourceOrNull(): JellyfinMediaSource? = currentMediaSource.value

    /**
     * Handle initial playback options from fragment.
     * Start of a playback session that can contain one or multiple played videos.
     *
     * @return an error of type [PlayerException] or null on success.
     */
    suspend fun initializePlaybackQueue(playOptions: PlayOptions): PlayerException? {
        currentQueue = playOptions.ids
        currentQueueIndex = playOptions.startIndex
        currentTrackNames = playOptions.trackNames

        val itemId = when {
            currentQueue.isNotEmpty() -> currentQueue[currentQueueIndex]
            else -> playOptions.mediaSourceId?.toUUIDOrNull()
        } ?: return PlayerException.InvalidPlayOptions()

        return startItem(
            itemId = itemId,
            fromDevice = playOptions.playFromDownloads == true,
            mediaSourceId = playOptions.mediaSourceId,
            maxStreamingBitrate = null,
            startTime = playOptions.startPosition,
            audioStreamIndex = playOptions.audioStreamIndex,
            subtitleStreamIndex = playOptions.subtitleStreamIndex,
        )
    }

    /**
     * Plays queue item [itemId]: from the device when [fromDevice] and it's downloaded, else
     * streamed. When streaming fails and it's downloaded, the download plays instead, e.g. when the
     * connection drops partway through a downloaded book.
     *
     * @return an error of type [PlayerException] or null on success.
     */
    @Suppress("LongParameterList")
    private suspend fun startItem(
        itemId: UUID,
        fromDevice: Boolean,
        mediaSourceId: String? = null,
        maxStreamingBitrate: Int?,
        startTime: Duration? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlayerException? {
        if (fromDevice && playDownloaded(itemId)) return null
        val error = startRemotePlayback(
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            maxStreamingBitrate = maxStreamingBitrate,
            startTime = startTime,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
        ) ?: return null
        return if (!fromDevice && playDownloaded(itemId)) null else error
    }

    /** Plays [itemId]'s download from where it was left, if its file is on the device. */
    private suspend fun playDownloaded(itemId: UUID): Boolean {
        val source = downloads.playable(itemId) ?: return false
        _currentMediaSource.value = source
        viewModel.load(source, prepareStreams(source), playWhenReady = true)
        return true
    }

    /**
     * Play a specific media item specified by [itemId] and [mediaSourceId].
     *
     * @return an error of type [PlayerException] or null on success.
     */
    private suspend fun startRemotePlayback(
        itemId: UUID,
        mediaSourceId: String?,
        maxStreamingBitrate: Int?,
        startTime: Duration? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        playWhenReady: Boolean = true,
    ): PlayerException? {
        mediaSourceResolver.resolveMediaSource(
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            deviceProfile = deviceProfile,
            maxStreamingBitrate = maxStreamingBitrate,
            startTime = startTime,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
        ).onSuccess { jellyfinMediaSource ->
            // Ensure transcoding of the current element is stopped (only relevant for remote sources)
            (getCurrentMediaSourceOrNull() as? RemoteJellyfinMediaSource)?.let { oldMediaSource ->
                viewModel.stopTranscoding(oldMediaSource)
            }

            _currentMediaSource.value = jellyfinMediaSource

            // Load new media source
            viewModel.load(jellyfinMediaSource, prepareStreams(jellyfinMediaSource), playWhenReady)
        }.onFailure { error ->
            // Should always be of this type, other errors are silently dropped
            return error as? PlayerException
        }
        return null
    }

    /**
     * Reinitialize current media source without changing settings
     */
    fun tryRestartPlayback() {
        with(getCurrentMediaSourceOrNull()) {
            when (this) {
                is LocalJellyfinMediaSource -> prepareStreams(this)
                is RemoteJellyfinMediaSource -> prepareStreams(this)
                null -> return
            }.let {
                viewModel.load(this, it, playWhenReady = true)
            }
        }
    }

    /**
     * Change the maximum bitrate to the specified value.
     */
    suspend fun changeBitrate(bitrate: Int?): Boolean {
        val currentMediaSource = getCurrentMediaSourceOrNull() as? RemoteJellyfinMediaSource ?: return false

        // Bitrate didn't change, ignore
        if (currentMediaSource.maxStreamingBitrate == bitrate) return true

        val currentPlayState = viewModel.getStateAndPause() ?: return false

        return startRemotePlayback(
            itemId = currentMediaSource.itemId,
            mediaSourceId = currentMediaSource.id,
            maxStreamingBitrate = bitrate,
            startTime = currentPlayState.position,
            audioStreamIndex = currentMediaSource.selectedAudioStreamIndex,
            subtitleStreamIndex = currentMediaSource.selectedSubtitleStreamIndex,
            playWhenReady = currentPlayState.playWhenReady,
        ) == null
    }

    fun hasPrevious(): Boolean = currentQueue.isNotEmpty() && currentQueueIndex > 0

    fun hasNext(): Boolean = currentQueue.isNotEmpty() && currentQueueIndex < currentQueue.lastIndex

    /** Plays the previous track the way the current one plays (from the device or streamed). */
    suspend fun previous(): Boolean {
        if (!hasPrevious()) return false
        val current = getCurrentMediaSourceOrNull() ?: return false
        return startItem(currentQueue[--currentQueueIndex], current.playsFromDevice, maxStreamingBitrate = current.bitrate) == null
    }

    /** Plays the next track the way the current one plays (from the device or streamed). */
    suspend fun next(): Boolean {
        if (!hasNext()) return false
        val current = getCurrentMediaSourceOrNull() ?: return false
        return startItem(currentQueue[++currentQueueIndex], current.playsFromDevice, maxStreamingBitrate = current.bitrate) == null
    }

    private val JellyfinMediaSource.playsFromDevice get() = this is LocalJellyfinMediaSource
    private val JellyfinMediaSource.bitrate get() = (this as? RemoteJellyfinMediaSource)?.maxStreamingBitrate

    suspend fun skipToIndex(index: Int): Boolean {
        if (index < 0 || index >= currentQueue.size) return false
        currentQueueIndex = index
        val current = getCurrentMediaSourceOrNull()
        return startItem(currentQueue[index], current?.playsFromDevice == true, maxStreamingBitrate = current?.bitrate) == null
    }

    /**
     * Builds the [MediaSource] to be played by ExoPlayer.
     *
     * @param source The [JellyfinMediaSource] object containing all necessary info about the item to be played.
     * @return A [MediaSource]. This can be the media stream of the correct type for the playback method or
     * a [MergingMediaSource] containing the mentioned media stream and all external subtitle streams.
     */
    @CheckResult
    private fun prepareStreams(source: LocalJellyfinMediaSource): MediaSource {
        val videoSource: MediaSource = createDownloadVideoMediaSource(source.id, source.remoteFileUri)
        val subtitleSources: Array<MediaSource> = createDownloadExternalSubtitleMediaSources(
            source,
            source.remoteFileUri,
        )
        return when {
            subtitleSources.isNotEmpty() -> MergingMediaSource(videoSource, *subtitleSources)
            else -> videoSource
        }
    }

    private fun prepareStreams(source: RemoteJellyfinMediaSource): MediaSource {
        val videoSource = createVideoMediaSource(source)
        val subtitleSources = createExternalSubtitleMediaSources(source)
        return when {
            subtitleSources.isNotEmpty() -> MergingMediaSource(videoSource, *subtitleSources)
            else -> videoSource
        }
    }

    /**
     * Builds the [MediaSource] for the main media stream (video/audio/embedded subs).
     *
     * @param source The [JellyfinMediaSource] object containing all necessary info about the item to be played.
     * @return A [MediaSource]. The type of MediaSource depends on the playback method/protocol.
     */
    @CheckResult
    private fun createVideoMediaSource(source: JellyfinMediaSource): MediaSource {
        val stream = apiClient.streamOf(source)
        val factory = when (stream.format) {
            StreamFormat.HLS -> get<HlsMediaSource.Factory>().setAllowChunklessPreparation(true)
            StreamFormat.PROGRESSIVE -> get<ProgressiveMediaSource.Factory>()
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(source.itemId.toString())
            .setUri(stream.url)
            .build()

        return factory.createMediaSource(mediaItem)
    }

    /**
     * Creates [MediaSource]s for all external subtitle streams in the [JellyfinMediaSource].
     *
     * @param source The [JellyfinMediaSource] object containing all necessary info about the item to be played.
     * @return The parsed MediaSources for the subtitles.
     */
    @CheckResult
    private fun createExternalSubtitleMediaSources(
        source: JellyfinMediaSource,
    ): Array<MediaSource> {
        val factory = get<SingleSampleMediaSource.Factory>()
        return source.externalSubtitleStreams.map { stream ->
            val uri = apiClient.createUrl(stream.deliveryUrl).toUri()
            val mediaItem = MediaItem.SubtitleConfiguration.Builder(uri).apply {
                setId("${ExternalSubtitleStream.ID_PREFIX}${stream.index}")
                setLabel(stream.displayTitle)
                setMimeType(stream.mimeType)
                setLanguage(stream.language)
            }.build()
            factory.createMediaSource(mediaItem, source.runTime.inWholeMilliseconds)
        }.toTypedArray()
    }

    @CheckResult
    private fun createDownloadVideoMediaSource(mediaSourceId: String, fileUri: String): MediaSource {
        val mediaSourceFactory: ProgressiveMediaSource.Factory = get()

        val mediaItem = MediaItem.Builder()
            .setMediaId(mediaSourceId)
            .setUri(fileUri.toUri())
            .build()

        return mediaSourceFactory.createMediaSource(mediaItem)
    }

    @CheckResult
    private fun createDownloadExternalSubtitleMediaSources(
        source: JellyfinMediaSource,
        fileUri: String,
    ): Array<MediaSource> {
        val downloadDir = File(fileUri).parent
        val factory = get<SingleSampleMediaSource.Factory>()
        return source.externalSubtitleStreams.map { stream ->
            val uri: Uri = File(downloadDir, "${stream.index}.subrip").toUri()
            val mediaItem = MediaItem.SubtitleConfiguration.Builder(uri).apply {
                setId("${ExternalSubtitleStream.ID_PREFIX}${stream.index}")
                setLabel(stream.displayTitle)
                setMimeType(stream.mimeType)
                setLanguage(stream.language)
            }.build()
            factory.createMediaSource(mediaItem, source.runTime.inWholeMilliseconds)
        }.toTypedArray()
    }

    /**
     * Seek to [position] in the current media source.
     *
     * For HLS-transcoded sources, `player.seekTo()` alone does not work: ExoPlayer requests
     * segments beyond what Jellyfin has transcoded so far, causing Jellyfin to restart the
     * transcode session whose timestamps start at 0, resetting the reported position.
     * Instead, we restart `startRemotePlayback` with the target position as `startTime` —
     * the same approach used by `changeBitrate`.
     *
     * For direct-play and direct-stream sources, native ExoPlayer seeking is sufficient.
     *
     * @return true if the seek was initiated.
     */
    suspend fun seekTo(position: Duration): Boolean {
        when (val currentMediaSource = getCurrentMediaSourceOrNull()) {
            is LocalJellyfinMediaSource -> viewModel.playerOrNull?.seekTo(position.inWholeMilliseconds)
            is RemoteJellyfinMediaSource -> {
                if (currentMediaSource.playMethod == PlayMethod.TRANSCODE) {
                    val wasPlaying = viewModel.playerOrNull?.playWhenReady ?: true
                    startRemotePlayback(
                        itemId = currentMediaSource.itemId,
                        mediaSourceId = currentMediaSource.id,
                        maxStreamingBitrate = currentMediaSource.maxStreamingBitrate,
                        startTime = position,
                        audioStreamIndex = currentMediaSource.selectedAudioStreamIndex,
                        subtitleStreamIndex = currentMediaSource.selectedSubtitleStreamIndex,
                        playWhenReady = wasPlaying,
                    )
                } else {
                    viewModel.playerOrNull?.seekTo(position.inWholeMilliseconds)
                }
            }
            null -> return false
        }
        return true
    }
}

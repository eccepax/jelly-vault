package org.jellyfin.mobile.player

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.DefaultAnalyticsCollector
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.app.PLAYER_EVENT_CHANNEL
import org.jellyfin.mobile.common.data.dao.UserDao
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.interaction.PlayerEvent
import org.jellyfin.mobile.player.interaction.PlayerLifecycleObserver
import org.jellyfin.mobile.player.interaction.PlayerMediaSessionCallback
import org.jellyfin.mobile.player.interaction.PlayerNotificationHelper
import org.jellyfin.mobile.player.mediasegments.MediaSegmentAction
import org.jellyfin.mobile.player.mediasegments.MediaSegmentRepository
import org.jellyfin.mobile.player.queue.QueueManager
import org.jellyfin.mobile.player.sleeptimer.SleepTimerManager
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.mobile.player.source.RemoteJellyfinMediaSource
import org.jellyfin.mobile.player.ui.AudiobookDisplayPreferences
import org.jellyfin.mobile.player.ui.PlayState
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.Constants.SUPPORTED_AUDIOBOOK_PLAYBACK_ACTIONS
import org.jellyfin.mobile.utils.applyDefaultAudioAttributes
import org.jellyfin.mobile.utils.applyDefaultLocalAudioAttributes
import org.jellyfin.mobile.utils.extensions.end
import org.jellyfin.mobile.utils.extensions.scaleInRange
import org.jellyfin.mobile.utils.extensions.start
import org.jellyfin.mobile.utils.extensions.width
import org.jellyfin.mobile.utils.getVolumeLevelPercent
import org.jellyfin.mobile.utils.getVolumeRange
import org.jellyfin.mobile.utils.setPlaybackState
import org.jellyfin.mobile.utils.toMediaMetadata
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.hlsSegmentApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.searchApi
import org.jellyfin.sdk.api.operations.HlsSegmentApi
import org.jellyfin.sdk.api.operations.PlayStateApi
import org.jellyfin.sdk.api.operations.SearchApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.extensions.inWholeTicks
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val SLEEP_TIMER_TICK_INTERVAL_MS = 1000L
private const val SLEEP_TIMER_NOTIFICATION_UPDATE_INTERVAL_TICKS = 60

/**
 * ViewModel specifically for audiobook playback
 * Uses audiobook-optimized skip intervals: 10s back, 30s forward
 * Based on PlayerViewModel but simplified for audio-only content
 *
 * Suppresses LargeClass: this is the app's central playback state machine (ExoPlayer lifecycle,
 * queue/session wiring, sleep timer, progress reporting). Splitting it is a real architectural
 * change with playback-regression risk, not a lint-driven refactor.
 */
@Suppress("TooManyFunctions", "LargeClass")
class AudiobookPlayerViewModel(application: Application) :
    AndroidViewModel(
        application,
    ),
    KoinComponent,
    Player.Listener,
    PlayerHost {
    override val app: Application
        get() = getApplication()
    private val apiClient: ApiClient = get()
    private val playStateApi: PlayStateApi = apiClient.playStateApi
    private val hlsSegmentApi: HlsSegmentApi = apiClient.hlsSegmentApi
    private val searchApi: SearchApi = apiClient.searchApi

    private val lifecycleObserver = PlayerLifecycleObserver(this)
    private val audioManager: AudioManager by lazy { getApplication<Application>().getSystemService()!! }
    val notificationHelper: PlayerNotificationHelper by lazy { PlayerNotificationHelper(this) }

    // Media source handling
    private val trackSelector = DefaultTrackSelector(getApplication())
    val trackSelectionHelper = TrackSelectionHelper(this, trackSelector)
    override val queueManager = QueueManager(this)
    override val mediaSourceOrNull: JellyfinMediaSource?
        get() = queueManager.getCurrentMediaSourceOrNull()
    private val mediaSegmentRepository: MediaSegmentRepository by inject()
    private val appPreferences: AudioVaultAppPreferences by inject()
    private val downloadDao: DownloadDao by inject()
    private val userDao: UserDao by inject()

    // ExoPlayer
    private val _player = MutableLiveData<ExoPlayer?>()
    private val _playerState = MutableLiveData<Int>()
    val player: LiveData<ExoPlayer?> get() = _player
    val playerState: LiveData<Int> get() = _playerState

    // Media Segments Ask to Skip
    private var askToSkipMediaSegments: List<MediaSegmentDto> = emptyList()

    private val _skipSegment = MutableStateFlow<MediaSegmentDto?>(null)

    /** The segment (an intro, an outro) being played that the player offers to skip; null when none. */
    val skipSegment: StateFlow<MediaSegmentDto?> = _skipSegment.asStateFlow()

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val eventLogger = EventLogger()
    private var analyticsCollector = buildAnalyticsCollector()
    private val initialTracksSelected = AtomicBoolean(false)
    private var fallbackPreferExtensionRenderers = false
    private var playSpeed = 1f

    private var progressUpdateJob: Job? = null
    private var skipMediaSegmentUpdateJob: Job? = null

    /**
     * For TRANSCODE (HLS) streams, Jellyfin starts the transcode from [JellyfinMediaSource.startTime]
     * but the HLS segments have timestamps relative to that point (starting from 0 in ExoPlayer's
     * internal clock). Every read of [ExoPlayer.currentPosition] must add this offset to obtain the
     * correct absolute position within the original audio file.
     *
     * Is 0 for DIRECT_PLAY / DIRECT_STREAM, where ExoPlayer seeks natively and the position is absolute.
     */
    private var transcodingStartOffset: Long = 0L

    /** Absolute playback position in the original audio file, in milliseconds. */
    val adjustedCurrentPosition: Long
        get() = (playerOrNull?.currentPosition ?: 0L) + transcodingStartOffset

    /** Absolute buffered position in the original audio file, in milliseconds. */
    val adjustedBufferedPosition: Long
        get() = (playerOrNull?.bufferedPosition ?: 0L) + transcodingStartOffset

    // Outlives viewModelScope so reportPlaybackStop() can complete after onCleared()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var reportStopJob: Job? = null

    /**
     * Returns the current ExoPlayer instance or null
     */
    override val playerOrNull: ExoPlayer? get() = _player.value

    private val playerEventChannel: Channel<PlayerEvent> by inject(named(PLAYER_EVENT_CHANNEL))

    override val mediaSession: MediaSession by lazy {
        MediaSession(
            getApplication<Application>().applicationContext,
            javaClass.simpleName.removePrefix(BuildConfig.APPLICATION_ID),
        ).apply {
            @Suppress("DEPRECATION")
            setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS or MediaSession.FLAG_HANDLES_MEDIA_BUTTONS)
            setCallback(mediaSessionCallback)
            applyDefaultLocalAudioAttributes(AudioAttributes.CONTENT_TYPE_MUSIC)
        }
    }
    private val mediaSessionCallback = PlayerMediaSessionCallback(this)

    override val allowBackgroundAudio: Boolean
        get() = true

    // Use audiobook-specific display preferences with optimized skip intervals
    private var displayPreferences = AudiobookDisplayPreferences()

    // Sleep timer
    val sleepTimerManager: SleepTimerManager by lazy { SleepTimerManager(this) }
    val isSleepTimerActive: Boolean get() = sleepTimerManager.isActive()
    private var sleepTimerUpdateJob: Job? = null

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        // Subscribe to player events
        viewModelScope.launch {
            for (event in playerEventChannel) {
                when (event) {
                    PlayerEvent.Pause -> mediaSessionCallback.onPause()
                    PlayerEvent.Resume -> mediaSessionCallback.onPlay()
                    PlayerEvent.Stop, PlayerEvent.Destroy -> mediaSessionCallback.onStop()
                    is PlayerEvent.Seek -> playerOrNull?.seekTo(event.duration.inWholeMilliseconds)
                    is PlayerEvent.SetVolume -> {
                        setVolume(event.volume)
                        playerOrNull?.reportPlaybackState()
                    }
                }
            }
        }
    }

    private fun buildAnalyticsCollector() = DefaultAnalyticsCollector(Clock.DEFAULT).apply {
        addListener(eventLogger)
    }

    /**
     * Setup a new [ExoPlayer] for audiobook playback, register callbacks and set attributes
     */
    override fun setupPlayer() {
        val renderersFactory = DefaultRenderersFactory(getApplication()).apply {
            setEnableDecoderFallback(true)
            val rendererMode = when {
                fallbackPreferExtensionRenderers -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            }
            setExtensionRendererMode(rendererMode)
        }
        _player.value = ExoPlayer.Builder(getApplication(), renderersFactory, get()).apply {
            setUsePlatformDiagnostics(false)
            setTrackSelector(trackSelector)
            setAnalyticsCollector(analyticsCollector)
        }.build().apply {
            addListener(this@AudiobookPlayerViewModel)
            applyDefaultAudioAttributes(C.AUDIO_CONTENT_TYPE_MUSIC)
            val savedSpeed = appPreferences.playbackSpeed
            if (savedSpeed != 1f) playbackParameters = playbackParameters.withSpeed(savedSpeed)
            playSpeed = savedSpeed
            skipSilenceEnabled = appPreferences.skipSilenceEnabled
        }
    }

    /**
     * Release the current ExoPlayer. MediaSession is intentionally kept alive so that
     * subsequent playback sessions (e.g. after stop → new item tap) can reuse it.
     * The session is released in [onCleared] when the Activity dies.
     */
    private fun releasePlayer() {
        notificationHelper.dismissNotification()
        mediaSession.isActive = false
        playerOrNull?.run {
            removeListener(this@AudiobookPlayerViewModel)
            release()
        }
        _player.value = null
    }

    override fun load(jellyfinMediaSource: JellyfinMediaSource, exoMediaSource: MediaSource, playWhenReady: Boolean) {
        // Player may be null after stop() — recreate it before loading new content.
        if (playerOrNull == null) setupPlayer()
        val player = playerOrNull ?: run {
            Timber.w("load() called but playerOrNull is still null after setupPlayer()")
            return
        }

        initialTracksSelected.set(false)

        val startTime = jellyfinMediaSource.startTime
        if (jellyfinMediaSource is RemoteJellyfinMediaSource &&
            jellyfinMediaSource.playMethod == PlayMethod.TRANSCODE
        ) {
            // For TRANSCODE (HLS): Jellyfin starts its transcoder at startTime, so the HLS
            // segments' timestamps are relative to that point (the stream appears to start at
            // t=0 to ExoPlayer). No seek needed — track the offset and add it everywhere we
            // read currentPosition.
            transcodingStartOffset = startTime.inWholeMilliseconds
            player.setMediaSource(exoMediaSource)
        } else {
            // DIRECT_PLAY / DIRECT_STREAM: pass the start position atomically with setMediaSource
            // so ExoPlayer buffers from that position rather than buffering from 0 and seeking later.
            transcodingStartOffset = 0L
            player.setMediaSource(exoMediaSource, startTime.inWholeMilliseconds)
        }

        player.prepare()

        applyMediaSegments(jellyfinMediaSource)

        player.playWhenReady = playWhenReady

        mediaSession.setMetadata(jellyfinMediaSource.toMediaMetadata())

        if (jellyfinMediaSource is RemoteJellyfinMediaSource) {
            viewModelScope.launch {
                player.reportPlaybackStart(jellyfinMediaSource)
            }
        }
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (true) {
                delay(Constants.PLAYER_TIME_UPDATE_RATE.coerceAtLeast(1L))
                playerOrNull?.reportPlaybackState()
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
    }

    private fun startSkipMediaSegmentUpdates() {
        skipMediaSegmentUpdateJob?.cancel()
        skipMediaSegmentUpdateJob = viewModelScope.launch {
            while (true) {
                delay(Constants.SKIP_MEDIA_SEGMENT_UPDATE_DELAY.coerceAtLeast(1L))
                playerOrNull?.updateSkipMediaSegmentButton()
            }
        }
    }

    private fun stopSkipMediaSegmentUpdates() {
        skipMediaSegmentUpdateJob?.cancel()
    }

    private suspend fun Player.reportPlaybackStart(mediaSource: RemoteJellyfinMediaSource) {
        try {
            val isPaused = !isPlaying
            withContext(Dispatchers.IO) {
                playStateApi.reportPlaybackStart(
                    PlaybackStartInfo(
                        itemId = mediaSource.itemId,
                        playMethod = mediaSource.playMethod,
                        playSessionId = mediaSource.playSessionId,
                        liveStreamId = mediaSource.liveStreamId,
                        audioStreamIndex = mediaSource.selectedAudioStream?.index,
                        subtitleStreamIndex = null, // Not needed for audiobooks
                        isPaused = isPaused,
                        isMuted = false,
                        canSeek = true,
                        positionTicks = mediaSource.startTime.inWholeTicks,
                        volumeLevel = audioManager.getVolumeLevelPercent(),
                        repeatMode = RepeatMode.REPEAT_NONE,
                        playbackOrder = PlaybackOrder.DEFAULT,
                    ),
                )
            }
        } catch (e: ApiClientException) {
            Timber.e(e, "Failed to report playback start")
        }
    }

    private fun Player.updateSkipMediaSegmentButton() {
        val mediaSegments = askToSkipMediaSegments
        if (mediaSegments.isEmpty()) return

        val playbackPosition = (currentPosition + this@AudiobookPlayerViewModel.transcodingStartOffset).milliseconds
        val currentMediaSegment = mediaSegments.find { seg -> playbackPosition in seg.start..seg.end }
        _skipSegment.value = currentMediaSegment
    }

    /**
     * Persists the current playback position for a downloaded item locally, since local sources
     * have no server-side [org.jellyfin.sdk.model.api.UserItemDataDto] to resume from.
     */
    private suspend fun persistLocalPlaybackPosition(
        mediaSource: LocalJellyfinMediaSource,
        position: Duration,
        finished: Boolean = false,
    ) {
        mediaSource.startTime = position
        // The server hasn't heard about this listening; OfflineListeningSync tells it later
        mediaSource.positionSavedAt = Instant.now()
        mediaSource.finished = finished
        withContext(Dispatchers.IO) {
            downloadDao.insert(DownloadEntity(mediaSource))
        }
    }

    private suspend fun Player.reportPlaybackState() {
        val localSource = mediaSourceOrNull as? LocalJellyfinMediaSource
        if (localSource != null) {
            if (playbackState != Player.STATE_ENDED) {
                val playbackPosition = (currentPosition + this@AudiobookPlayerViewModel.transcodingStartOffset).milliseconds
                persistLocalPlaybackPosition(localSource, playbackPosition)
            }
            return
        }

        val mediaSource = mediaSourceOrNull as? RemoteJellyfinMediaSource ?: return
        val playbackPosition = (currentPosition + this@AudiobookPlayerViewModel.transcodingStartOffset).milliseconds
        if (playbackState != Player.STATE_ENDED) {
            val stream = AudioManager.STREAM_MUSIC
            val volumeRange = audioManager.getVolumeRange(stream)
            val currentVolume = audioManager.getStreamVolume(stream)
            val isPaused = !isPlaying
            try {
                withContext(Dispatchers.IO) {
                    playStateApi.reportPlaybackProgress(
                        PlaybackProgressInfo(
                            itemId = mediaSource.itemId,
                            playMethod = mediaSource.playMethod,
                            playSessionId = mediaSource.playSessionId,
                            liveStreamId = mediaSource.liveStreamId,
                            audioStreamIndex = mediaSource.selectedAudioStream?.index,
                            subtitleStreamIndex = null, // Not needed for audiobooks
                            isPaused = isPaused,
                            isMuted = false,
                            canSeek = true,
                            positionTicks = playbackPosition.inWholeTicks,
                            volumeLevel = (currentVolume - volumeRange.first) * Constants.PERCENT_MAX / volumeRange.width,
                            repeatMode = RepeatMode.REPEAT_NONE,
                            playbackOrder = PlaybackOrder.DEFAULT,
                        ),
                    )
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to report playback progress")
            }
        }
    }

    private fun reportPlaybackStop() {
        val player = playerOrNull ?: return

        val localSource = mediaSourceOrNull as? LocalJellyfinMediaSource
        if (localSource != null) {
            val hasFinished = player.playbackState == Player.STATE_ENDED
            // Restart from the beginning next time if the book finished, mirroring server behavior.
            val lastPosition = when {
                hasFinished -> Duration.ZERO
                else -> (player.currentPosition + transcodingStartOffset).milliseconds
            }
            reportStopJob?.cancel()
            reportStopJob = cleanupScope.launch {
                persistLocalPlaybackPosition(localSource, lastPosition, finished = hasFinished)
            }
            return
        }

        val mediaSource = mediaSourceOrNull as? RemoteJellyfinMediaSource ?: return
        val hasFinished = player.playbackState == Player.STATE_ENDED
        val lastPositionTicks = when {
            hasFinished -> mediaSource.runTime.inWholeTicks
            else -> (player.currentPosition + transcodingStartOffset).milliseconds.inWholeTicks
        }

        reportStopJob?.cancel()
        reportStopJob = cleanupScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    playStateApi.reportPlaybackStopped(
                        PlaybackStopInfo(
                            itemId = mediaSource.itemId,
                            positionTicks = lastPositionTicks,
                            playSessionId = mediaSource.playSessionId,
                            liveStreamId = mediaSource.liveStreamId,
                            failed = false,
                        ),
                    )
                }

                if (hasFinished) {
                    withContext(Dispatchers.IO) {
                        playStateApi.markPlayedItem(itemId = mediaSource.itemId)
                    }
                }

                stopTranscoding(mediaSource)
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to report playback stop")
            }
        }
    }

    override suspend fun stopTranscoding(mediaSource: RemoteJellyfinMediaSource) {
        if (mediaSource.playMethod == PlayMethod.TRANSCODE) {
            withContext(Dispatchers.IO) {
                hlsSegmentApi.stopEncodingProcess(
                    deviceId = apiClient.deviceInfo.id,
                    playSessionId = mediaSource.playSessionId,
                )
            }
        }
    }

    private fun applyMediaSegments(jellyfinMediaSource: JellyfinMediaSource) {
        askToSkipMediaSegments = emptyList()
        _skipSegment.value = null

        viewModelScope.launch {
            if (jellyfinMediaSource.item != null) {
                val mediaSegments = mediaSegmentRepository.getSegmentsForItem(jellyfinMediaSource.item)
                val newAskToSkipMediaSegments = mutableListOf<MediaSegmentDto>()

                for (mediaSegment in mediaSegments) {
                    val action = mediaSegmentRepository.getMediaSegmentAction(mediaSegment)

                    when (action) {
                        MediaSegmentAction.SKIP -> addSkipAction(mediaSegment)
                        MediaSegmentAction.ASK_TO_SKIP -> newAskToSkipMediaSegments.add(mediaSegment)
                        MediaSegmentAction.NOTHING -> Unit
                    }
                }

                askToSkipMediaSegments = newAskToSkipMediaSegments
            }
        }
    }

    /**
     * [MediaSegmentRepository.getSegmentsForItem] is an async network fetch, so playback may
     * already have advanced past (or into) this segment's window by the time its SKIP action is
     * known. An ExoPlayer message scheduled at a position the player has already passed never
     * fires, so a segment we're already inside is skipped immediately instead; one already fully
     * passed is left alone.
     */
    private fun addSkipAction(mediaSegment: MediaSegmentDto) {
        val player = playerOrNull ?: return
        val playbackPosition = (player.currentPosition + transcodingStartOffset).milliseconds

        when {
            playbackPosition >= mediaSegment.end -> Unit
            playbackPosition >= mediaSegment.start -> player.seekTo(mediaSegment.end.inWholeMilliseconds)
            else -> {
                player
                    .createMessage { _, _ ->
                        viewModelScope.launch(Dispatchers.Main) {
                            player.seekTo(mediaSegment.end.inWholeMilliseconds)
                        }
                    }
                    .setPosition(mediaSegment.start.inWholeMilliseconds.coerceAtLeast(1))
                    .setDeleteAfterDelivery(false)
                    .send()
            }
        }
    }

    // Player controls with audiobook-specific skip intervals
    override fun play() {
        playerOrNull?.play()
    }

    override fun pause() {
        playerOrNull?.pause()
    }

    /**
     * Seek to an absolute position in the original audio file.
     *
     * Routes through [QueueManager.seekTo] for TRANSCODE (HLS) streams, which restarts the
     * Jellyfin transcode session from the target position (ExoPlayer cannot seek within an
     * in-progress transcode). Uses native ExoPlayer seeking for DIRECT_PLAY / DIRECT_STREAM.
     *
     * [positionMs] must be an **absolute** position (i.e. already includes [transcodingStartOffset]).
     */
    fun seekToPosition(positionMs: Long) {
        if ((mediaSourceOrNull as? RemoteJellyfinMediaSource)?.playMethod == PlayMethod.TRANSCODE) {
            viewModelScope.launch { queueManager.seekTo(positionMs.milliseconds) }
        } else {
            playerOrNull?.seekTo(positionMs)
        }
    }

    /**
     * Rewind by audiobook skip back interval (10 seconds)
     */
    override fun rewind() {
        val newPosition = (adjustedCurrentPosition - displayPreferences.skipBackLength).coerceAtLeast(0L)
        seekToPosition(newPosition)
    }

    /**
     * Fast forward by audiobook skip forward interval (30 seconds)
     */
    override fun fastForward() {
        val totalDurationMs = mediaSourceOrNull?.runTime?.inWholeMilliseconds?.takeIf { it > 0 } ?: Long.MAX_VALUE
        val newPosition = (adjustedCurrentPosition + displayPreferences.skipForwardLength).coerceAtMost(totalDurationMs)
        seekToPosition(newPosition)
    }

    fun previousChapter() {
        val chapters = mediaSourceOrNull?.item?.chapters
        if (!chapters.isNullOrEmpty()) {
            val currentPosition = playerOrNull?.currentPosition?.milliseconds ?: return
            val skipToPreviousDuration = Constants.MAX_SKIP_TO_PREV_CHAPTER_MS.milliseconds
            val playbackPosition = currentPosition - skipToPreviousDuration
            if (playbackPosition < Duration.ZERO) {
                skipToPrevious()
            } else {
                val seekToPosition = ChapterNavigation.currentChapterStartPosition(chapters, playbackPosition) ?: return
                playerOrNull?.seekTo(seekToPosition.inWholeMilliseconds)
            }
        } else {
            skipToPrevious()
        }
    }

    fun nextChapter() {
        val chapters = mediaSourceOrNull?.item?.chapters
        if (!chapters.isNullOrEmpty()) {
            val currentPosition = playerOrNull?.currentPosition?.milliseconds ?: return
            val playbackPosition = ChapterNavigation.nextChapterStartPosition(chapters, currentPosition) ?: return
            if (playbackPosition == Duration.INFINITE) {
                skipToNext()
            } else {
                playerOrNull?.seekTo(playbackPosition.inWholeMilliseconds)
            }
        } else {
            skipToNext()
        }
    }

    fun skipToQueueItem(index: Int) {
        viewModelScope.launch {
            queueManager.skipToIndex(index)
        }
    }

    /**
     * Handles a voice-search play request (e.g. "Hey Google, play [book] on AudioVault" via
     * Android Auto / Assistant). A blank query (a generic "play" with no search text) resumes
     * whatever's already loaded; otherwise resolves the top server-side search hit and starts it.
     */
    override fun playFromSearch(query: String) {
        if (query.isBlank()) {
            play()
            return
        }

        viewModelScope.launch {
            val userId = resolveCurrentUserId() ?: return@launch
            val hint = runCatching {
                withContext(Dispatchers.IO) {
                    searchApi.getSearchHints(
                        userId = userId,
                        searchTerm = query,
                        includeItemTypes = listOf(BaseItemKind.AUDIO_BOOK, BaseItemKind.AUDIO),
                        limit = 1,
                    ).content.searchHints.firstOrNull()
                }
            }.getOrNull() ?: return@launch

            queueManager.initializePlaybackQueue(
                PlayOptions(
                    ids = listOf(hint.id),
                    mediaSourceId = null,
                    startIndex = 0,
                    startPosition = null,
                    audioStreamIndex = null,
                    subtitleStreamIndex = null,
                    playFromDownloads = false,
                    trackNames = listOfNotNull(hint.name),
                ),
            )
        }
    }

    private suspend fun resolveCurrentUserId(): UUID? {
        val serverId = appPreferences.currentServerId ?: return null
        val userIdDbId = appPreferences.currentUserId ?: return null
        val serverUser = withContext(Dispatchers.IO) { userDao.getServerUser(serverId, userIdDbId) }
        return serverUser?.user?.userId?.let(UUID::fromString)
    }

    override fun skipToPrevious() {
        val player = playerOrNull ?: return
        when {
            player.currentPosition <= Constants.MAX_SKIP_TO_PREV_MS -> viewModelScope.launch {
                pause()
                if (!queueManager.previous()) {
                    playerOrNull?.seekTo(0)
                    play()
                }
            }
            else -> player.seekTo(0)
        }
    }

    override fun skipToNext() {
        viewModelScope.launch {
            queueManager.next()
        }
    }

    fun skipMediaSegment(mediaSegmentDto: MediaSegmentDto?) {
        val player = playerOrNull ?: return
        val mediaSegment = mediaSegmentDto ?: return
        player.seekTo(mediaSegment.end.inWholeMilliseconds + 1)
    }

    override fun getStateAndPause(): PlayState? {
        val player = playerOrNull ?: return null

        val playWhenReady = player.playWhenReady
        player.pause()
        val position = (player.contentPosition + transcodingStartOffset).milliseconds

        return PlayState(playWhenReady, position)
    }

    /**
     * Set the playback speed to [speed]
     *
     * @return true if the speed was changed
     */
    fun setPlaybackSpeed(speed: Float): Boolean {
        val player = playerOrNull ?: return false
        val parameters = player.playbackParameters
        if (parameters.speed != speed) {
            player.playbackParameters = parameters.withSpeed(speed)
            playSpeed = speed
            appPreferences.playbackSpeed = speed
            return true
        }
        return false
    }

    fun setPressSpeedUp(isPressing: Boolean, speed: Float): Boolean {
        val player = playerOrNull ?: return false
        if (!isPressing) {
            // Restore user's base speed without persisting (it's already saved)
            player.playbackParameters = player.playbackParameters.withSpeed(playSpeed)
            return true
        }
        // Apply hold speed directly — don't touch playSpeed so restore works correctly
        player.playbackParameters = player.playbackParameters.withSpeed(speed)
        return true
    }

    /**
     * Enable or disable skipping silent passages during playback.
     *
     * @return true if the setting was changed
     */
    fun setSkipSilenceEnabled(enabled: Boolean): Boolean {
        val player = playerOrNull ?: return false
        if (player.skipSilenceEnabled != enabled) {
            player.skipSilenceEnabled = enabled
            appPreferences.skipSilenceEnabled = enabled
            return true
        }
        return false
    }

    override fun stop() {
        pause()
        reportPlaybackStop()
        releasePlayer()
    }

    private fun setVolume(percent: Int) {
        if (audioManager.isVolumeFixed) return
        val stream = AudioManager.STREAM_MUSIC
        val volumeRange = audioManager.getVolumeRange(stream)
        val scaled = volumeRange.scaleInRange(percent)
        audioManager.setStreamVolume(stream, scaled, 0)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        val player = playerOrNull ?: return

        _playerState.value = playbackState

        if (playbackState == Player.STATE_READY) {
            if (!initialTracksSelected.getAndSet(true)) {
                trackSelectionHelper.selectInitialTracks()
            }
            mediaSession.isActive = true
            notificationHelper.postNotification()
        }

        updateProgressJobs(player.playWhenReady, playbackState)
        updateMediaSessionPlaybackState(player)

        viewModelScope.launch {
            when (playbackState) {
                Player.STATE_READY, Player.STATE_BUFFERING -> {
                    player.reportPlaybackState()
                }
                Player.STATE_ENDED -> {
                    reportPlaybackStop()
                    if (!queueManager.next()) {
                        releasePlayer()
                    }
                }
            }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        val player = playerOrNull ?: return
        if (!playWhenReady) {
            viewModelScope.launch { player.reportPlaybackState() }
        }
        updateProgressJobs(playWhenReady, player.playbackState)
        updateMediaSessionPlaybackState(player)
        // Re-post the notification so its play/pause action reflects the new state. Without this,
        // a pause that isn't accompanied by a STATE_READY transition (e.g. transient audio focus
        // loss when the screen locks) leaves the lock-screen/notification button showing "pause"
        // even though playback has actually stopped.
        notificationHelper.postNotification(sleepTimerManager.getRemainingTime())
    }

    /**
     * Start or stop the server-reporting and UI-helper jobs based on whether the player is
     * actively playing (STATE_READY + playWhenReady).
     */
    private fun updateProgressJobs(playWhenReady: Boolean, playbackState: Int) {
        if (playbackState == Player.STATE_READY && playWhenReady) {
            startProgressUpdates()
            if (askToSkipMediaSegments.isNotEmpty()) {
                startSkipMediaSegmentUpdates()
            }
        } else {
            stopProgressUpdates()
            stopSkipMediaSegmentUpdates()
        }
    }

    private fun updateMediaSessionPlaybackState(player: Player) {
        var playbackActions = SUPPORTED_AUDIOBOOK_PLAYBACK_ACTIONS
        if (queueManager.hasPrevious()) {
            playbackActions = playbackActions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        }
        if (queueManager.hasNext()) {
            playbackActions = playbackActions or PlaybackState.ACTION_SKIP_TO_NEXT
        }
        mediaSession.setPlaybackState(player, playbackActions)
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        super.onPositionDiscontinuity(oldPosition, newPosition, reason)
        playerOrNull?.updateSkipMediaSegmentButton()
    }

    override fun onPlayerError(error: PlaybackException) {
        reportPlaybackStop()
        _error.postValue(error.localizedMessage.orEmpty())
    }

    override fun onCleared() {
        reportPlaybackStop()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        releasePlayer()
        mediaSession.release()
    }

    // Sleep timer controls
    fun startSleepTimer(duration: SleepTimerManager.SleepTimerDuration) {
        sleepTimerManager.startTimer(duration)
        updateSleepTimerRemainingTime()
        notificationHelper.postNotification(sleepTimerManager.getRemainingTime())
    }

    fun startSleepTimerEndOfChapter() {
        sleepTimerManager.startTimerEndOfChapter()
        updateSleepTimerRemainingTime()
        notificationHelper.postNotification(sleepTimerManager.getRemainingTime())
    }

    fun cancelSleepTimer() {
        sleepTimerManager.cancelTimer()
        notificationHelper.postNotification()
    }

    private fun updateSleepTimerRemainingTime() {
        sleepTimerUpdateJob?.cancel()
        sleepTimerUpdateJob = viewModelScope.launch {
            var tickCount = 0
            while (sleepTimerManager.isActive()) {
                val remaining = sleepTimerManager.getRemainingTime()
                if (tickCount % SLEEP_TIMER_NOTIFICATION_UPDATE_INTERVAL_TICKS == 0 && tickCount > 0) {
                    notificationHelper.postNotification(remaining)
                }
                tickCount++
                delay(SLEEP_TIMER_TICK_INTERVAL_MS)
            }
            notificationHelper.postNotification()
        }
    }
}

package org.jellyfin.mobile.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.cast.CastSource
import org.jellyfin.mobile.cast.Casting
import org.jellyfin.mobile.cast.SessionCasting
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.jellyfin.mobile.common.downloads.WakeModeFollowsTrack
import org.jellyfin.mobile.common.downloads.wakeModeFor
import org.jellyfin.mobile.player.forAudiobooks
import org.jellyfin.mobile.player.session.withPlayableUri
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Hosts the ExoPlayer behind a MediaSession, so a book keeps playing with the app swiped away, and
 * the notification, lock screen, Bluetooth and Assistant controls all work. The app talks to it
 * through a MediaController (see [DeferredAudiobookController]); Media3 runs the foreground service
 * and the media notification while it plays.
 */
@UnstableApi
class AudiobookPlaybackService : MediaSessionService() {

    private val mediaSourceFactory: MediaSource.Factory by inject()
    private val downloads: ItemDownloads by inject()
    private val preferences: AudioVaultAppPreferences by inject()
    private val voiceSearch: AudiobookVoiceSearch by inject()
    private val audiobooks: JellyfinAudiobooks by inject()
    private val appController: DeferredAudiobookController by inject()
    private val casting: Casting by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var session: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var sessionCasting: SessionCasting? = null

    override fun onCreate() {
        super.onCreate()
        session = newSession()
        // Started from outside the app (a voice search, a media button): the app follows it too
        appController.connect()
    }

    private fun newSession(): MediaSession {
        val exoPlayer = ExoPlayer.Builder(this, renderersFactory(), mediaSourceFactory)
            .forAudiobooks()
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .setWakeMode(wakeModeFor(onDevice = false))
            .build()
            .apply {
                setPlaybackSpeed(preferences.playbackSpeed)
                skipSilenceEnabled = preferences.skipSilenceEnabled
                // A track playing from the device needs only the CPU kept awake; one streaming needs Wi-Fi too
                addListener(WakeModeFollowsTrack({ downloads.fileFor(it.mediaId) != null }, ::setWakeMode))
            }

        val audiobookPlayer = AudiobookPlayer(TranscodeSeekingPlayer(exoPlayer))
        return MediaSession.Builder(this, audiobookPlayer)
            .setCallback(AudiobookSessionCallback(exoPlayer, voiceSearch::bookFor, audiobooks::streamUrl, scope))
            .setMediaButtonPreferences(mediaButtons())
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
            .also { session ->
                this.exoPlayer = exoPlayer
                sessionCasting = SessionCasting.attach(
                    session,
                    exoPlayer,
                    casting,
                    // Chapters on either player; transcode seeking only on this device's, whose wrapper
                    // holds what it's waiting to seek to (a cast player starts and seeks its own streams)
                    wrap = { player -> if (player === exoPlayer) audiobookPlayer else AudiobookPlayer(player) },
                    castSource = ::castSource,
                    castUri = audiobooks::castUri,
                )
            }
    }

    /** Where the cast device fetches [item] from: the server, even when it plays from a download here. */
    private suspend fun castSource(item: MediaItem): CastSource? =
        item.toAudiobookTrack().id.takeIf { it.isNotEmpty() }?.let { audiobooks.castSource(it) }

    /** Uses the FFmpeg decoders for what the device can't play itself. */
    private fun renderersFactory() = DefaultRenderersFactory(this)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

    /** Skip back and forward either side of play, and the chapters in the overflow. */
    private fun mediaButtons(): List<CommandButton> = listOf(
        button(
            CommandButton.ICON_SKIP_BACK_10,
            Player.COMMAND_SEEK_BACK,
            R.string.player_rewind,
            CommandButton.SLOT_BACK,
        ),
        button(
            CommandButton.ICON_SKIP_FORWARD_30,
            Player.COMMAND_SEEK_FORWARD,
            R.string.player_fast_forward,
            CommandButton.SLOT_FORWARD,
        ),
        button(
            CommandButton.ICON_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            R.string.player_previous_chapter,
            CommandButton.SLOT_OVERFLOW,
        ),
        button(
            CommandButton.ICON_NEXT,
            Player.COMMAND_SEEK_TO_NEXT,
            R.string.player_next_chapter,
            CommandButton.SLOT_OVERFLOW,
        ),
    )

    private fun button(icon: Int, command: Int, name: Int, slot: Int) = CommandButton.Builder(icon)
        .setPlayerCommand(command)
        .setDisplayName(getString(name))
        .setSlots(slot)
        .build()

    /** A new session if the last was let go of, for a controller connecting again before the service stops. */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        session ?: newSession().also { session = it }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away with nothing playing (paused, finished, stopped by an error) shouldn't
        // leave a dead notification behind: the session goes, and with it the notification and the
        // app's controller, which kept the service bound
        if (session?.player?.isPlaybackOngoing() != true) {
            releaseSession()
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        releaseSession()
        super.onDestroy()
    }

    private fun releaseSession() {
        // The session's player is either of these, so each is released here rather than through it
        sessionCasting?.release()
        exoPlayer?.release()
        session?.release()
        sessionCasting = null
        exoPlayer = null
        session = null
    }

    companion object {
        const val SEEK_BACK_MS = 10_000L
        const val SEEK_FORWARD_MS = 30_000L
    }
}

/**
 * Takes what isn't a plain player command: items from a controller (whose streams it restores),
 * books asked for by voice, and the app's own commands.
 */
@UnstableApi
class AudiobookSessionCallback(
    private val exoPlayer: ExoPlayer,
    /** The book that best matches a spoken request, ready to play; null when nothing does. */
    private val findBook: suspend (query: String) -> AudiobookQueue?,
    private val streamUrl: (itemId: String) -> String,
    private val scope: CoroutineScope,
) : MediaSession.Callback {

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
        MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(SET_SKIP_SILENCE).build(),
            )
            .build()

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> =
        Futures.immediateFuture(mediaItems.map { it.withPlayableUri() }.toMutableList())

    /**
     * "Play [book] on AudioVault": the best match plays from where it was left. Without a book named,
     * the one already loaded carries on.
     */
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val query = mediaItems.singleOrNull()?.requestMetadata?.searchQuery
            ?: return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        if (query.isBlank()) return Futures.immediateFuture(loaded(mediaSession.player))

        val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
            val book = try {
                findBook(query)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.w(e, "Couldn't search for \"%s\"", query)
                result.setException(e)
                return@launch
            }
            if (book == null) {
                result.setException(NoSuchElementException("Nothing matches \"$query\""))
            } else {
                val items = book.tracks.map { it.toMediaItem(streamUrl) }
                result.set(MediaSession.MediaItemsWithStartPosition(items, book.startIndex, book.startPositionMs))
            }
        }.invokeOnCompletion { cause ->
            // The service stopped before the search was done, or even started
            if (cause != null) result.setException(cause)
        }
        return result
    }

    private fun loaded(player: Player) = MediaSession.MediaItemsWithStartPosition(
        (0 until player.mediaItemCount).map(player::getMediaItemAt),
        player.currentMediaItemIndex,
        player.currentPosition,
    )

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (customCommand.customAction != SET_SKIP_SILENCE.customAction) {
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
        // This device's player only, also while casting: the Default Media Receiver can't skip silence,
        // so it plays a cast as it is, and the setting is this device's once playback is back here
        exoPlayer.skipSilenceEnabled = args.getBoolean(KEY_ENABLED)
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    companion object {
        /** Turns skipping silent passages on or off, as [KEY_ENABLED] says. */
        val SET_SKIP_SILENCE = SessionCommand("org.jellyfin.mobile.SET_SKIP_SILENCE", Bundle.EMPTY)
        const val KEY_ENABLED = "enabled"
    }
}

/** Playing, or about to once it has loaded: not paused, finished, or stopped by an error. */
private fun Player.isPlaybackOngoing(): Boolean =
    playWhenReady && mediaItemCount > 0 && (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)

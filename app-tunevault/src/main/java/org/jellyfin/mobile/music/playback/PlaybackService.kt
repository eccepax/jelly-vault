package org.jellyfin.mobile.music.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.jellyfin.mobile.cast.Casting
import org.jellyfin.mobile.cast.SessionCasting
import org.jellyfin.mobile.common.downloads.DownloadAwareDataSource
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.jellyfin.mobile.common.downloads.WakeModeFollowsTrack
import org.jellyfin.mobile.common.downloads.wakeModeFor
import org.jellyfin.mobile.music.TuneMainActivity
import org.jellyfin.mobile.music.catalog.JellyfinMusicCatalog
import org.jellyfin.mobile.music.downloads.TrackCacheKey
import org.jellyfin.mobile.player.session.withPlayableUri
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

/**
 * Hosts the ExoPlayer behind a MediaSession, so playback continues in the background and the
 * lock screen, notification, Bluetooth and Android Auto controls all work. The app's UI talks to it
 * through a MediaController (see [DeferredPlaybackController]); Media3 runs the foreground service
 * and media notification while it plays.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private val downloads: ItemDownloads by inject()
    private var session: MediaSession? = null
    private var player: ExoPlayer? = null
    private var casting: SessionCasting? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(downloadAwareDataSource()))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // handleAudioFocus: pause for calls/other apps, duck for navigation prompts.
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(wakeModeFor(onDevice = false))
            .build()
        // A downloaded song needs only the CPU kept awake; one streaming needs Wi-Fi too
        player.addListener(WakeModeFollowsTrack({ it.trackId?.let(downloads::fileFor) != null }, player::setWakeMode))
        this.player = player

        val session = MediaSession.Builder(this, player)
            .setCallback(StreamUriCallback)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, TuneMainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        this.session = session
        // The cast device gets its own url for each song, asking only for what it plays, and its type
        val catalog = get<JellyfinMusicCatalog>()
        casting = SessionCasting.attach(session, player, get<Casting>(), castSource = { item ->
            item.trackId?.let { catalog.castSource(it) }
        })
    }

    /** Fully downloaded songs play from the device; anything else streams. */
    private fun downloadAwareDataSource(): DataSource.Factory = DownloadAwareDataSource.Factory(
        itemIdOf = TrackCacheKey::trackIdOf,
        fileFor = downloads::fileFor,
        onDevice = FileDataSource.Factory(),
        server = DefaultDataSource.Factory(this),
    )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away while paused shouldn't leave a dead notification behind.
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        // The session's player is either of these, so each is released here rather than through it
        casting?.release()
        player?.release()
        session?.release()
        casting = null
        player = null
        session = null
        super.onDestroy()
    }

    /** MediaController -> MediaSession strips each item's uri; restore it from requestMetadata. */
    private object StreamUriCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.withPlayableUri() }.toMutableList())
    }
}

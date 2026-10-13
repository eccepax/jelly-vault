package org.jellyfin.mobile.player

import android.app.Application
import android.media.session.MediaSession
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.RemoteJellyfinMediaSource
import org.jellyfin.mobile.player.ui.PlayState

/**
 * Shared surface area required by player helpers (notification, queue, media session, etc).
 *
 * `AudiobookPlayerViewModel` implements this.
 *
 * Suppresses TooManyFunctions: this is the single cohesive playback-control contract consumed by
 * the notification, media session, and queue helpers — splitting it would spread transport
 * controls across multiple interfaces with no real gain.
 */
@Suppress("TooManyFunctions")
interface PlayerHost {
    /**
     * Avoids clashing with AndroidViewModel's generic getApplication().
     */
    val app: Application

    val playerOrNull: ExoPlayer?
    val queueManager: org.jellyfin.mobile.player.queue.QueueManager
    val mediaSession: MediaSession
    val mediaSourceOrNull: JellyfinMediaSource?

    val allowBackgroundAudio: Boolean

    fun setupPlayer()
    fun play()
    fun pause()
    fun rewind()
    fun fastForward()
    fun skipToPrevious()
    fun skipToNext()
    fun stop()
    fun playFromSearch(query: String)

    fun load(jellyfinMediaSource: JellyfinMediaSource, exoMediaSource: MediaSource, playWhenReady: Boolean)
    fun logTracks()

    fun getStateAndPause(): PlayState?
    suspend fun stopTranscoding(mediaSource: RemoteJellyfinMediaSource)
}

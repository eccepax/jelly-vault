package org.jellyfin.mobile.cast

import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi

/**
 * Passes every event on to [listener], with the queue in it as [RemotePlaylist] sent it: the
 * timeline through [sentTimeline], places in it through [sentPosition], and the song moved to and
 * its metadata from [sentCurrent]. A
 * media session publishes the timeline an event hands it, so this is what keeps its controllers
 * (an app's own player screen, its progress sync) off the cast player's placeholders.
 */
// One override per Player.Listener method: anything not passed on would be lost
@Suppress("TooManyFunctions", "DEPRECATION", "OVERRIDE_DEPRECATION")
@UnstableApi
internal class SentQueueListener(
    private val listener: Player.Listener,
    private val sentTimeline: (Timeline) -> Timeline,
    private val sentPosition: (Player.PositionInfo) -> Player.PositionInfo,
    private val sentCurrent: () -> MediaItem?,
) : Player.Listener {

    private var lastMetadata: MediaMetadata? = null

    override fun onTimelineChanged(timeline: Timeline, reason: Int) =
        listener.onTimelineChanged(sentTimeline(timeline), reason)

    /**
     * The player's own items all have the same (empty) metadata, so it never says the metadata
     * changed with the song: moving to a song whose sent metadata is new says so here.
     */
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val sent = sentCurrent()
        listener.onMediaItemTransition(mediaItem?.let { sent ?: it }, reason)
        sent?.mediaMetadata?.takeIf { it != lastMetadata }?.let(::onMediaMetadataChanged)
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        val metadata = sentCurrent()?.mediaMetadata ?: mediaMetadata
        lastMetadata = metadata
        listener.onMediaMetadataChanged(metadata)
    }

    override fun onEvents(player: Player, events: Player.Events) = listener.onEvents(player, events)
    override fun onTracksChanged(tracks: Tracks) = listener.onTracksChanged(tracks)
    override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) = listener.onPlaylistMetadataChanged(
        mediaMetadata,
    )
    override fun onIsLoadingChanged(isLoading: Boolean) = listener.onIsLoadingChanged(isLoading)
    override fun onLoadingChanged(isLoading: Boolean) = listener.onLoadingChanged(isLoading)
    override fun onAvailableCommandsChanged(availableCommands: Player.Commands) =
        listener.onAvailableCommandsChanged(availableCommands)
    override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) =
        listener.onTrackSelectionParametersChanged(parameters)
    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) =
        listener.onPlayerStateChanged(playWhenReady, playbackState)
    override fun onPlaybackStateChanged(playbackState: Int) = listener.onPlaybackStateChanged(playbackState)
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) =
        listener.onPlayWhenReadyChanged(playWhenReady, reason)
    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) =
        listener.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
    override fun onIsPlayingChanged(isPlaying: Boolean) = listener.onIsPlayingChanged(isPlaying)
    override fun onRepeatModeChanged(repeatMode: Int) = listener.onRepeatModeChanged(repeatMode)
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) =
        listener.onShuffleModeEnabledChanged(shuffleModeEnabled)
    override fun onPlayerError(error: PlaybackException) = listener.onPlayerError(error)
    override fun onPlayerErrorChanged(error: PlaybackException?) = listener.onPlayerErrorChanged(error)
    override fun onPositionDiscontinuity(reason: Int) = listener.onPositionDiscontinuity(reason)
    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) =
        listener.onPositionDiscontinuity(sentPosition(oldPosition), sentPosition(newPosition), reason)
    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) =
        listener.onPlaybackParametersChanged(playbackParameters)
    override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) =
        listener.onSeekBackIncrementChanged(seekBackIncrementMs)
    override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) =
        listener.onSeekForwardIncrementChanged(seekForwardIncrementMs)
    override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) =
        listener.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)
    override fun onAudioSessionIdChanged(audioSessionId: Int) = listener.onAudioSessionIdChanged(audioSessionId)
    override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) =
        listener.onAudioAttributesChanged(audioAttributes)
    override fun onVolumeChanged(volume: Float) = listener.onVolumeChanged(volume)
    override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) =
        listener.onSkipSilenceEnabledChanged(skipSilenceEnabled)
    override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) = listener.onDeviceInfoChanged(deviceInfo)
    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) = listener.onDeviceVolumeChanged(volume, muted)
    override fun onVideoSizeChanged(videoSize: VideoSize) = listener.onVideoSizeChanged(videoSize)
    override fun onSurfaceSizeChanged(width: Int, height: Int) = listener.onSurfaceSizeChanged(width, height)
    override fun onRenderedFirstFrame() = listener.onRenderedFirstFrame()
    override fun onCues(cues: List<Cue>) = listener.onCues(cues)
    override fun onCues(cueGroup: CueGroup) = listener.onCues(cueGroup)
    override fun onMetadata(metadata: Metadata) = listener.onMetadata(metadata)

    // Equal to the listener it wraps, so a ForwardingPlayer finds it again to remove it
    override fun equals(other: Any?): Boolean = other is SentQueueListener && other.listener == listener
    override fun hashCode(): Int = listener.hashCode()
}

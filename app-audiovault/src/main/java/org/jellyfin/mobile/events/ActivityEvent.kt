package org.jellyfin.mobile.events

import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource

sealed class ActivityEvent {
    class LaunchNativePlayer(val playOptions: PlayOptions) : ActivityEvent()
    class OpenUrl(val uri: String) : ActivityEvent()

    /** Downloads a whole book at once, so the network preference and notification permission are asked once. */
    class DownloadTracks(val trackIds: List<String>) : ActivityEvent()
    class RemoveTracks(val trackIds: List<String>) : ActivityEvent()
    class RemoveDownload(val download: LocalJellyfinMediaSource, val force: Boolean = false) : ActivityEvent()
    data object OpenSettings : ActivityEvent()
    data object SelectServer : ActivityEvent()
    data object ExitApp : ActivityEvent()
    data object OpenDownloads : ActivityEvent()
    data object ResumePlayer : ActivityEvent()
}

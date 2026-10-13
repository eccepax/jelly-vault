package org.jellyfin.mobile.events

import android.net.Uri
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource

sealed class ActivityEvent {
    class LaunchNativePlayer(val playOptions: PlayOptions) : ActivityEvent()
    class OpenUrl(val uri: String) : ActivityEvent()

    /** All [files] in one batch resolve the network-type preference and notification permission exactly once. */
    class DownloadFiles(val files: List<DownloadFileSpec>) : ActivityEvent()
    class RemoveDownload(val download: LocalJellyfinMediaSource, val force: Boolean = false) : ActivityEvent()
    data object RequestBluetoothPermission : ActivityEvent()
    data object OpenSettings : ActivityEvent()
    data object SelectServer : ActivityEvent()
    data object ExitApp : ActivityEvent()
    data object OpenDownloads : ActivityEvent()
    data object ResumePlayer : ActivityEvent()
}

data class DownloadFileSpec(val uri: Uri, val filename: String)

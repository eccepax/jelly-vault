package org.jellyfin.mobile.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.ui.rememberAskForNotificationsOnce
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Downloading, and removing the download of, a book's tracks. */
interface DownloadRequests {
    fun download(trackIds: List<String>)
    fun remove(trackIds: List<String>)
}

/**
 * [DownloadRequests] that settle what a download needs once for the whole book, not per track:
 * showing its progress (asked once). Which networks it may use is Settings' Wi-Fi only switch.
 */
@Composable
fun rememberDownloadRequests(
    preferences: AudioVaultAppPreferences = koinInject(),
    /** In a view model, so leaving the library, or a rotation, doesn't cut a book's download short. */
    download: (List<String>) -> Unit = koinViewModel<DownloadsViewModel>()::download,
    remove: (List<String>) -> Unit = koinViewModel<DownloadsViewModel>()::remove,
): DownloadRequests {
    val askForNotifications = rememberAskForNotificationsOnce(
        alreadyAsked = { preferences.askedForNotifications },
        markAsked = { preferences.askedForNotifications = true },
    )
    val startDownload by rememberUpdatedState(download)
    val startRemove by rememberUpdatedState(remove)

    return remember {
        object : DownloadRequests {
            override fun download(trackIds: List<String>) {
                if (trackIds.isEmpty()) return
                askForNotifications()
                startDownload(trackIds)
            }

            override fun remove(trackIds: List<String>) {
                startRemove(trackIds)
            }
        }
    }
}

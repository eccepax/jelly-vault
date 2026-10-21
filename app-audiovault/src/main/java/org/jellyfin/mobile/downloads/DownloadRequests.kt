package org.jellyfin.mobile.downloads

import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.media3.exoplayer.offline.DownloadManager
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.ui.rememberAskForNotificationsOnce
import org.koin.compose.koinInject

/** Downloading, and removing the download of, a book's tracks. */
interface DownloadRequests {
    fun download(trackIds: List<String>)
    fun remove(trackIds: List<String>)
}

/**
 * [DownloadRequests] that settle what a download needs once for the whole book, not per track: which
 * networks it may use (asked the first time, then kept), and showing its progress (asked once).
 */
@Composable
fun rememberDownloadRequests(
    preferences: AudioVaultAppPreferences = koinInject(),
    /** Lets downloads already waiting use the networks chosen too. */
    applyMethod: (Int) -> Unit = koinInject<DownloadManager>().let { manager -> { manager.applyDownloadMethod(it) } },
    download: suspend (List<String>) -> Unit = koinInject<AudiobookDownloads>()::download,
    remove: suspend (String) -> Unit = koinInject<AudiobookDownloads>()::remove,
): DownloadRequests {
    val scope = rememberCoroutineScope()
    val askForNotifications = rememberAskForNotificationsOnce(
        alreadyAsked = { preferences.askedForNotifications },
        markAsked = { preferences.askedForNotifications = true },
    )
    val startDownload by rememberUpdatedState(download)
    val startRemove by rememberUpdatedState(remove)
    val applyNow by rememberUpdatedState(applyMethod)
    // The book waiting on which networks it may use; saved, so a rotation doesn't drop the question
    var waiting by rememberSaveable { mutableStateOf<List<String>?>(null) }

    fun go(trackIds: List<String>, method: Int) {
        askForNotifications()
        applyNow(method)
        scope.launch { startDownload(trackIds) }
    }

    waiting?.let { trackIds ->
        ChooseNetwork { method ->
            preferences.downloadMethod = method
            waiting = null
            go(trackIds, method)
        }
    }

    return remember {
        object : DownloadRequests {
            override fun download(trackIds: List<String>) {
                if (trackIds.isEmpty()) return
                val method = preferences.downloadMethod
                if (method == null) waiting = trackIds else go(trackIds, method)
            }

            override fun remove(trackIds: List<String>) {
                scope.launch { trackIds.forEach { startRemove(it) } }
            }
        }
    }
}

/** Asked before the first download: it must be answered, there's no download without it. */
@Composable
private fun ChooseNetwork(onChoose: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.network_title)) },
        text = { Text(stringResource(R.string.network_message)) },
        confirmButton = {
            TextButton(onClick = { onChoose(DownloadMethod.WIFI_ONLY) }) { Text(stringResource(R.string.wifi_only)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onChoose(DownloadMethod.MOBILE_DATA) },
            ) { Text(stringResource(R.string.mobile_data)) }
        },
        backgroundColor = MaterialTheme.colors.surface,
    )
}

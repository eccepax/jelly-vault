package org.jellyfin.mobile.common.downloads

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.reflect.KMutableProperty0

/** The parts of downloading that Settings controls. */
interface DownloadSettings {
    val bytesUsed: Flow<Long>

    /** Whether there's anything to remove, even downloads that haven't fetched a byte yet. */
    val hasDownloads: Flow<Boolean>

    /** The saved Wi-Fi only choice; setting it saves it and applies it to downloads already waiting too. */
    var wifiOnly: Boolean

    suspend fun removeAll()
}

/**
 * [DownloadSettings] over an app's [manager], with the Wi-Fi only choice kept in [savedWifiOnly];
 * [removeAll] also forgets whatever the app keeps beside the files.
 */
@UnstableApi
class Media3DownloadSettings(
    private val manager: DownloadManager,
    private val savedWifiOnly: KMutableProperty0<Boolean>,
    override val bytesUsed: Flow<Long>,
    override val hasDownloads: Flow<Boolean>,
    private val removeAll: suspend () -> Unit,
) : DownloadSettings {

    override var wifiOnly: Boolean
        get() = savedWifiOnly.get()
        set(value) {
            savedWifiOnly.set(value)
            manager.requirements = downloadRequirements(value)
        }

    // Finishes even if the screen that asked goes away, so nothing is left half removed
    override suspend fun removeAll() = withContext(NonCancellable) { removeAll.invoke() }
}

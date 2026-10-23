package org.jellyfin.mobile.common.downloads

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import kotlinx.coroutines.flow.Flow

/** The parts of downloading that Settings controls. */
interface DownloadSettings {
    val bytesUsed: Flow<Long>

    /** Applies the Wi-Fi only choice to downloads already waiting, not just new ones. */
    fun applyWifiOnly(wifiOnly: Boolean)

    suspend fun removeAll()
}

/** [DownloadSettings] over an app's [manager]; [removeAll] also forgets whatever the app keeps beside the files. */
@UnstableApi
class Media3DownloadSettings(
    private val manager: DownloadManager,
    override val bytesUsed: Flow<Long>,
    private val removeAll: suspend () -> Unit,
) : DownloadSettings {

    override fun applyWifiOnly(wifiOnly: Boolean) {
        manager.requirements = downloadRequirements(wifiOnly)
    }

    override suspend fun removeAll() = removeAll.invoke()
}

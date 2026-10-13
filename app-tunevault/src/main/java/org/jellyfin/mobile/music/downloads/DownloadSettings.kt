package org.jellyfin.mobile.music.downloads

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

@UnstableApi
class Media3DownloadSettings(
    private val repository: DownloadRepository,
    private val manager: DownloadManager,
) : DownloadSettings {
    override val bytesUsed: Flow<Long> get() = repository.bytesUsed

    override fun applyWifiOnly(wifiOnly: Boolean) {
        manager.requirements = downloadRequirements(wifiOnly)
    }

    override suspend fun removeAll() = repository.removeAll()
}

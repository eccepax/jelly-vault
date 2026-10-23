package org.jellyfin.mobile.testing.downloads

import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.common.downloads.DownloadSettings

class FakeDownloadSettings : DownloadSettings {
    override val bytesUsed = MutableStateFlow(0L)
    val events = mutableListOf<String>()

    override fun applyWifiOnly(wifiOnly: Boolean) {
        events += "wifiOnly $wifiOnly"
    }

    override suspend fun removeAll() {
        events += "removeAll"
        bytesUsed.value = 0L
    }
}

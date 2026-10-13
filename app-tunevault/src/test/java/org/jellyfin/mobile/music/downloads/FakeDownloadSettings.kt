package org.jellyfin.mobile.music.downloads

import kotlinx.coroutines.flow.MutableStateFlow

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

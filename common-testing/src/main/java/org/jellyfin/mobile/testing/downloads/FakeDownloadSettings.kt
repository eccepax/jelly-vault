package org.jellyfin.mobile.testing.downloads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import org.jellyfin.mobile.common.downloads.DownloadSettings

class FakeDownloadSettings : DownloadSettings {
    override val bytesUsed = MutableStateFlow(0L)

    /** Downloads queued that haven't fetched anything yet, e.g. waiting for Wi-Fi. */
    val queued = MutableStateFlow(false)
    override val hasDownloads = combine(bytesUsed, queued) { bytes, queued -> bytes > 0L || queued }
    val events = mutableListOf<String>()

    override var wifiOnly = true
        set(value) {
            field = value
            events += "wifiOnly $value"
        }

    override suspend fun removeAll() {
        events += "removeAll"
        bytesUsed.value = 0L
        queued.value = false
    }
}

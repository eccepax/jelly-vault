package org.jellyfin.mobile.music.app

import org.jellyfin.mobile.music.domain.StreamingQuality

class FakePlaybackPreferences : PlaybackPreferences {
    override var streamingQuality = StreamingQuality.ORIGINAL
    override var downloadQuality = StreamingQuality.ORIGINAL
    override var downloadOnWifiOnly = true
    override var showSleepTimer = true
    override var volumeLevelling = true
    override var removeDownloadsOnSignOut = false
}

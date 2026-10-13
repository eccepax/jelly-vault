package org.jellyfin.mobile.music.app

import android.content.Context
import androidx.core.content.edit
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.music.domain.AlbumSort
import org.jellyfin.mobile.music.domain.StreamingQuality

/** Library choices that persist between launches. ViewModels depend on this, not on SharedPreferences. */
interface LibraryPreferences {
    var albumSort: AlbumSort
}

/** Playback choices that persist between launches. */
interface PlaybackPreferences {
    var streamingQuality: StreamingQuality

    /** Separate from streaming: data saver shouldn't shrink what you keep. */
    var downloadQuality: StreamingQuality
    var downloadOnWifiOnly: Boolean

    /** Whether Now Playing offers the sleep timer. */
    var showSleepTimer: Boolean

    /** Whether songs play at a similar loudness (see VolumeLeveller). */
    var volumeLevelling: Boolean
}

class TuneAppPreferences(context: Context) : AppPreferences(context), LibraryPreferences, PlaybackPreferences {
    override var albumSort: AlbumSort
        get() = sharedPreferences.getString(PREF_ALBUM_SORT, null)
            ?.let { stored -> AlbumSort.entries.firstOrNull { it.name == stored } }
            ?: AlbumSort.TITLE
        set(value) = sharedPreferences.edit { putString(PREF_ALBUM_SORT, value.name) }

    override var streamingQuality: StreamingQuality
        get() = sharedPreferences.getString(PREF_STREAMING_QUALITY, null)
            ?.let { stored -> StreamingQuality.entries.firstOrNull { it.name == stored } }
            ?: StreamingQuality.ORIGINAL
        set(value) = sharedPreferences.edit { putString(PREF_STREAMING_QUALITY, value.name) }

    override var downloadQuality: StreamingQuality
        get() = sharedPreferences.getString(PREF_DOWNLOAD_QUALITY, null)
            ?.let { stored -> StreamingQuality.entries.firstOrNull { it.name == stored } }
            ?: StreamingQuality.ORIGINAL
        set(value) = sharedPreferences.edit { putString(PREF_DOWNLOAD_QUALITY, value.name) }

    override var downloadOnWifiOnly: Boolean
        get() = sharedPreferences.getBoolean(PREF_DOWNLOAD_WIFI_ONLY, true)
        set(value) = sharedPreferences.edit { putBoolean(PREF_DOWNLOAD_WIFI_ONLY, value) }

    override var volumeLevelling: Boolean
        get() = sharedPreferences.getBoolean(PREF_VOLUME_LEVELLING, true)
        set(value) = sharedPreferences.edit { putBoolean(PREF_VOLUME_LEVELLING, value) }

    override var showSleepTimer: Boolean
        get() = sharedPreferences.getBoolean(PREF_SHOW_SLEEP_TIMER, true)
        set(value) = sharedPreferences.edit { putBoolean(PREF_SHOW_SLEEP_TIMER, value) }

    /** Whether TuneVault has asked to show notifications (see shouldAskForNotifications). */
    var askedForNotifications: Boolean
        get() = sharedPreferences.getBoolean(PREF_ASKED_NOTIFICATIONS, false)
        set(value) = sharedPreferences.edit { putBoolean(PREF_ASKED_NOTIFICATIONS, value) }

    private companion object {
        const val PREF_ASKED_NOTIFICATIONS = "asked_notifications"
        const val PREF_SHOW_SLEEP_TIMER = "show_sleep_timer"
        const val PREF_VOLUME_LEVELLING = "volume_levelling"
        const val PREF_ALBUM_SORT = "album_sort"
        const val PREF_STREAMING_QUALITY = "streaming_quality"
        const val PREF_DOWNLOAD_QUALITY = "download_quality"
        const val PREF_DOWNLOAD_WIFI_ONLY = "download_wifi_only"
    }
}

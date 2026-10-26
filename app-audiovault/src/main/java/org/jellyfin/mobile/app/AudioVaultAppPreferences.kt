package org.jellyfin.mobile.app

import android.content.Context
import androidx.core.content.edit
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.player.mediasegments.MediaSegmentAction
import org.jellyfin.mobile.player.mediasegments.toMediaSegmentActionsString
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.model.api.MediaSegmentType

class AudioVaultAppPreferences(context: Context) : AppPreferences(context) {

    init {
        // Settings that no longer exist, still in upgraded installs (and their backups)
        if (RETIRED_PREFS.any(sharedPreferences::contains)) {
            sharedPreferences.edit { RETIRED_PREFS.forEach { remove(it) } }
        }
        // Before the Wi-Fi only switch, a choice of networks: any but Wi-Fi only let mobile data in.
        // Read without getInt, which would throw on every launch for a value kept as anything else.
        if (sharedPreferences.contains(OLD_DOWNLOAD_METHOD)) {
            val method = sharedPreferences.all[OLD_DOWNLOAD_METHOD] as? Int
            sharedPreferences.edit {
                if (method != null) putBoolean(PREF_DOWNLOAD_WIFI_ONLY, method == OLD_WIFI_ONLY)
                remove(OLD_DOWNLOAD_METHOD)
            }
        }
    }

    var mediaSegmentActions: String
        get() = sharedPreferences.getString(
            Constants.PREF_MEDIA_SEGMENT_ACTIONS,
            mapOf(
                MediaSegmentType.INTRO to MediaSegmentAction.ASK_TO_SKIP,
                MediaSegmentType.OUTRO to MediaSegmentAction.ASK_TO_SKIP,
            ).toMediaSegmentActionsString(),
        )!!
        set(value) = sharedPreferences.edit { putString(Constants.PREF_MEDIA_SEGMENT_ACTIONS, value) }

    var exoPlayerAllowPressSpeedUp: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_EXOPLAYER_ALLOW_PRESS_SPEED_UP, true)
        set(value) = sharedPreferences.edit { putBoolean(Constants.PREF_EXOPLAYER_ALLOW_PRESS_SPEED_UP, value) }

    var librarySortOption: String
        get() = sharedPreferences.getString(Constants.PREF_LIBRARY_SORT_OPTION, null) ?: "TITLE"
        set(value) = sharedPreferences.edit { putString(Constants.PREF_LIBRARY_SORT_OPTION, value) }

    var libraryGlobalFlattened: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_LIBRARY_GLOBAL_FLATTEN, true)
        set(value) = sharedPreferences.edit { putBoolean(Constants.PREF_LIBRARY_GLOBAL_FLATTEN, value) }

    var playbackSpeed: Float
        get() = sharedPreferences.getFloat(Constants.PREF_PLAYBACK_SPEED, 1f)
        set(value) = sharedPreferences.edit { putFloat(Constants.PREF_PLAYBACK_SPEED, value) }

    var skipSilenceEnabled: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_SKIP_SILENCE, false)
        set(value) = sharedPreferences.edit { putBoolean(Constants.PREF_SKIP_SILENCE, value) }

    /** Whether AudioVault has asked to show notifications (see common shouldAskForNotifications). */
    var askedForNotifications: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_ASKED_NOTIFICATIONS, false)
        set(value) = sharedPreferences.edit { putBoolean(Constants.PREF_ASKED_NOTIFICATIONS, value) }

    private companion object {
        /** The Bluetooth permission prompt, removed in favour of pausing when headphones go */
        val RETIRED_PREFS = listOf("pref_ignore_bluetooth_permission")

        /** The networks downloads could use, before the Wi-Fi only switch: 0 was Wi-Fi only */
        const val OLD_DOWNLOAD_METHOD = "pref_download_method"
        const val OLD_WIFI_ONLY = 0
    }
}

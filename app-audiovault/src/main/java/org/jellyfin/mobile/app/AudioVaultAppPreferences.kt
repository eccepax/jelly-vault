package org.jellyfin.mobile.app

import android.content.Context
import androidx.core.content.edit
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.player.mediasegments.MediaSegmentAction
import org.jellyfin.mobile.player.mediasegments.toMediaSegmentActionsString
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.model.api.MediaSegmentType

class AudioVaultAppPreferences(context: Context) : AppPreferences(context) {

    var downloadMethod: Int?
        get() = sharedPreferences.getInt(Constants.PREF_DOWNLOAD_METHOD, -1).takeIf { it >= 0 }
        set(value) {
            if (value != null) {
                sharedPreferences.edit { putInt(Constants.PREF_DOWNLOAD_METHOD, value) }
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

    val chromecastEnabled: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_CHROMECAST_ENABLED, false)

    var playbackSpeed: Float
        get() = sharedPreferences.getFloat(Constants.PREF_PLAYBACK_SPEED, 1f)
        set(value) = sharedPreferences.edit { putFloat(Constants.PREF_PLAYBACK_SPEED, value) }

    var skipSilenceEnabled: Boolean
        get() = sharedPreferences.getBoolean(Constants.PREF_SKIP_SILENCE, false)
        set(value) = sharedPreferences.edit { putBoolean(Constants.PREF_SKIP_SILENCE, value) }
}

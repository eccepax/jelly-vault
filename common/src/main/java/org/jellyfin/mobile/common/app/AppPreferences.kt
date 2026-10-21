package org.jellyfin.mobile.common.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

open class AppPreferences(context: Context) {
    protected val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    var currentServerId: Long?
        get() = sharedPreferences.getLong(PREF_SERVER_ID, -1).takeIf { it >= 0 }
        set(value) {
            sharedPreferences.edit {
                if (value != null) putLong(PREF_SERVER_ID, value) else remove(PREF_SERVER_ID)
            }
        }

    var currentUserId: Long?
        get() = sharedPreferences.getLong(PREF_USER_ID, -1).takeIf { it >= 0 }
        set(value) {
            sharedPreferences.edit {
                if (value != null) putLong(PREF_USER_ID, value) else remove(PREF_USER_ID)
            }
        }

    /** Off by default: downloads stay on the device when signing out. Settings can turn it on. */
    var removeDownloadsOnSignOut: Boolean
        get() = sharedPreferences.getBoolean(PREF_REMOVE_DOWNLOADS_ON_SIGN_OUT, false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_REMOVE_DOWNLOADS_ON_SIGN_OUT, value)
            }
        }

    companion object {
        const val PREF_SERVER_ID = "pref_server_id"
        const val PREF_USER_ID = "pref_user_id"
        const val PREF_INSTANCE_URL = "pref_instance_url"
        const val PREF_REMOVE_DOWNLOADS_ON_SIGN_OUT = "pref_remove_downloads_on_sign_out"
        const val FRAGMENT_CONNECT_EXTRA_ERROR = "org.jellyfin.mobile.intent.extra.ERROR"
    }
}

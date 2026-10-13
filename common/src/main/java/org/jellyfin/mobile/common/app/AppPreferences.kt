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

    var ignoreBluetoothPermission: Boolean
        get() = sharedPreferences.getBoolean(PREF_IGNORE_BLUETOOTH_PERMISSION, false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(PREF_IGNORE_BLUETOOTH_PERMISSION, value)
            }
        }

    companion object {
        const val PREF_SERVER_ID = "pref_server_id"
        const val PREF_USER_ID = "pref_user_id"
        const val PREF_INSTANCE_URL = "pref_instance_url"
        const val PREF_IGNORE_BLUETOOTH_PERMISSION = "pref_ignore_bluetooth_permission"
        const val FRAGMENT_CONNECT_EXTRA_ERROR = "org.jellyfin.mobile.intent.extra.ERROR"
    }
}

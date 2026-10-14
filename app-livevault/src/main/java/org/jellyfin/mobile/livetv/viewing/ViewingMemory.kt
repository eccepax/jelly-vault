package org.jellyfin.mobile.livetv.viewing

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the viewer was watching and browsing, kept for the next time the app opens. */
interface ViewingMemory {
    /** The last channel that played; null when none has (or it was forgotten). */
    val lastChannelId: StateFlow<String?>

    /** The tag the channel list was narrowed to, or null for every channel. */
    val tag: String?
    val favoritesOnly: Boolean

    fun rememberChannel(channelId: String)

    fun rememberFilter(tag: String?, favoritesOnly: Boolean)

    /** Signing out: the next account starts fresh. */
    fun forget()
}

/** [ViewingMemory] in SharedPreferences. */
class PreferencesViewingMemory(context: Context) : ViewingMemory {

    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _lastChannelId = MutableStateFlow(preferences.getString(KEY_CHANNEL, null))
    override val lastChannelId: StateFlow<String?> = _lastChannelId.asStateFlow()

    override val tag: String? get() = preferences.getString(KEY_TAG, null)
    override val favoritesOnly: Boolean get() = preferences.getBoolean(KEY_FAVORITES, false)

    override fun rememberChannel(channelId: String) {
        preferences.edit { putString(KEY_CHANNEL, channelId) }
        _lastChannelId.value = channelId
    }

    override fun rememberFilter(tag: String?, favoritesOnly: Boolean) {
        preferences.edit {
            putString(KEY_TAG, tag)
            putBoolean(KEY_FAVORITES, favoritesOnly)
        }
    }

    override fun forget() {
        preferences.edit { clear() }
        _lastChannelId.value = null
    }

    private companion object {
        const val FILE = "viewing"
        const val KEY_CHANNEL = "last_channel"
        const val KEY_TAG = "tag"
        const val KEY_FAVORITES = "favorites_only"
    }
}

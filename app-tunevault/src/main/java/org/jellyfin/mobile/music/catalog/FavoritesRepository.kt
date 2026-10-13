package org.jellyfin.mobile.music.catalog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The app's one view of favorites. A change shows at once (an override on top of what the server
 * last said) and is saved in the background; if the save fails the override is put back. Every
 * screen asks this, so hearts agree everywhere.
 */
class FavoritesRepository(
    private val store: FavoritesStore,
    private val scope: CoroutineScope,
) {
    private val _overrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** Changes made in this session, by item id; anything absent is as the server reported. */
    val overrides: StateFlow<Map<String, Boolean>> = _overrides.asStateFlow()

    fun isFavorite(itemId: String, serverValue: Boolean): Boolean = _overrides.value[itemId] ?: serverValue

    /** Forgets every change, at sign-out, so the next account starts from its own server values. */
    fun clear() {
        _overrides.value = emptyMap()
    }

    fun setFavorite(itemId: String, favorite: Boolean) {
        val previous = _overrides.value[itemId]
        _overrides.update { it + (itemId to favorite) }
        scope.launch {
            try {
                store.setFavorite(itemId, favorite)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Couldn't save favorite for $itemId")
                _overrides.update { if (previous == null) it - itemId else it + (itemId to previous) }
            }
        }
    }
}

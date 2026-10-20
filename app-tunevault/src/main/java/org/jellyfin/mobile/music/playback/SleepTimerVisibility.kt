package org.jellyfin.mobile.music.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.mobile.common.playback.SleepTimer
import org.jellyfin.mobile.music.app.PlaybackPreferences

/** Changing whether the sleep timer is shown, which is all Settings needs. */
fun interface ShowSleepTimer {
    fun setShown(shown: Boolean)
}

/**
 * Whether Now Playing shows the sleep timer (a setting). Hiding it also turns off a running timer:
 * a timer you can no longer see shouldn't be able to pause the music.
 */
class SleepTimerVisibility(
    private val preferences: PlaybackPreferences,
    private val timer: SleepTimer,
) : ShowSleepTimer {

    private val _isShown = MutableStateFlow(preferences.showSleepTimer)
    val isShown: StateFlow<Boolean> = _isShown.asStateFlow()

    override fun setShown(shown: Boolean) {
        preferences.showSleepTimer = shown
        _isShown.value = shown
        if (!shown) timer.cancel()
    }
}

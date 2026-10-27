package org.jellyfin.mobile.music.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jellyfin.mobile.music.app.PlaybackPreferences
import kotlin.math.pow

private const val DECIBELS_PER_DECADE = 20f

/**
 * The player volume that applies [gainDb], the server's measured adjustment to a common loudness.
 * Never above full: the player's volume can only turn sound down, so a quiet song stays a little
 * quiet rather than being boosted (which could also clip). Unmeasured songs play as they are.
 */
fun levelFor(gainDb: Float?): Float =
    gainDb?.let { 10f.pow(it / DECIBELS_PER_DECADE).coerceAtMost(1f) } ?: 1f

/**
 * The player's volume, made of two parts that multiply: volume levelling and the sleep timer's
 * fade. Each sets its own part, so neither overwrites the other.
 *
 * Sent while casting too: the cast player leaves the cast device's own volume alone, and keeps it
 * for this device's player once playback is back (common-cast's RemotePlaylist and
 * SessionPlayerSwitcher).
 */
class PlaybackVolume(private val playback: PlaybackController) {
    private var level = 1f
    private var fade = 1f
    private var applied = 1f

    fun setLevel(level: Float) {
        this.level = level
        apply()
    }

    fun setFade(fade: Float) {
        this.fade = fade
        apply()
    }

    private fun apply() {
        val volume = level * fade
        if (volume == applied) return
        applied = volume
        playback.setVolume(volume)
    }
}

/** Plays each song at a similar loudness while [enabled], using its measured gain. */
class VolumeLeveller(
    private val playback: PlaybackController,
    private val volume: PlaybackVolume,
    private val enabled: StateFlow<Boolean>,
    private val scope: CoroutineScope,
) {
    fun start(): Job = scope.launch {
        val currentGain = playback.state
            .map { state -> state.queue.entries.getOrNull(state.queue.currentIndex)?.track?.normalizationGainDb }
            .distinctUntilChanged()
        combine(currentGain, enabled) { gain, on -> if (on) levelFor(gain) else 1f }
            .distinctUntilChanged()
            .collect(volume::setLevel)
    }
}

/** Switching levelling on or off, which is all Settings needs. */
fun interface SetVolumeLevelling {
    fun setOn(on: Boolean)
}

/** The volume-levelling setting, kept as a flow so a change applies to the playing song at once. */
class VolumeLevellingSetting(private val preferences: PlaybackPreferences) : SetVolumeLevelling {
    private val _isOn = MutableStateFlow(preferences.volumeLevelling)
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    override fun setOn(on: Boolean) {
        preferences.volumeLevelling = on
        _isOn.value = on
    }
}

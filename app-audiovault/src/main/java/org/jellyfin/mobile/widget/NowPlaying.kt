package org.jellyfin.mobile.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.jellyfin.mobile.player.playback.AudiobookTrack

/** What the widget shows of the book loaded: its [title], the part playing ([detail]), and whether it plays. */
data class NowPlaying(val title: String, val detail: String?, val isPlaying: Boolean)

/** The book [track] is a part of, as the widget shows it; null with nothing loaded. */
fun nowPlayingOf(track: AudiobookTrack?, isPlaying: Boolean): NowPlaying? = track?.let {
    val book = it.bookTitle?.takeIf { title -> title.isNotBlank() && title != it.title }
    NowPlaying(title = book ?: it.title, detail = it.title.takeIf { book != null }, isPlaying = isPlaying)
}

private val TITLE = stringPreferencesKey("now_playing_title")
private val DETAIL = stringPreferencesKey("now_playing_detail")
private val PLAYING = booleanPreferencesKey("now_playing_playing")

/** Keeps [now] (or that nothing plays) in a widget's state, for [nowPlayingFrom] to read. */
fun MutablePreferences.keep(now: NowPlaying?) {
    if (now == null) {
        remove(TITLE)
        remove(DETAIL)
        remove(PLAYING)
        return
    }
    this[TITLE] = now.title
    if (now.detail != null) this[DETAIL] = now.detail else remove(DETAIL)
    this[PLAYING] = now.isPlaying
}

/** What a widget's state says is playing (see [keep]); null for nothing. */
fun nowPlayingFrom(state: Preferences): NowPlaying? =
    state[TITLE]?.let { NowPlaying(title = it, detail = state[DETAIL], isPlaying = state[PLAYING] == true) }

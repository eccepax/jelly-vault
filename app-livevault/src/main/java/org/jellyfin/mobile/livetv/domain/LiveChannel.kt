package org.jellyfin.mobile.livetv.domain

import java.time.Instant

/** A Live TV channel as the browser shows it. */
data class LiveChannel(
    val id: String,
    /** The tuner's channel number, e.g. "4" or the sub-channel "4.1". */
    val number: String,
    val name: String,
    /** Null when the server has no logo for the channel. */
    val logoUrl: String?,
    /** Server-side channel tags (My Teams, Sports, Free, ...). */
    val tags: List<String>,
    val isFavorite: Boolean,
    /** What's airing on the channel, or null when its guide has nothing for now. */
    val nowPlaying: LiveProgram?,
)

data class LiveProgram(
    val title: String,
    val start: Instant,
    val end: Instant,
)

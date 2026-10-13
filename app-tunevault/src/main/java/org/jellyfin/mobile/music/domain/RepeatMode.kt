package org.jellyfin.mobile.music.domain

enum class RepeatMode {
    OFF,
    ALL,
    ONE,
    ;

    /** The mode a single "repeat" button tap moves to. */
    fun next(): RepeatMode = entries[(ordinal + 1) % entries.size]
}

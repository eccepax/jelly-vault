package org.jellyfin.mobile.music.domain

import java.time.Instant

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int?,
    val coverUrl: String? = null,
    val dateAdded: Instant? = null,
    val isFavorite: Boolean = false,
)

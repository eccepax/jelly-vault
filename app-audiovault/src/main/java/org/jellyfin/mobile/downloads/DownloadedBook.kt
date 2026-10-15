package org.jellyfin.mobile.downloads

import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.library.AudiobookEntryBuilder

/**
 * The downloaded tracks of [track]'s book, in play order: those in the same folder with the same
 * album, as the library groups them. A track the server told nothing about is a book of its own.
 */
internal fun downloadedBookOf(track: DownloadEntity, downloads: List<DownloadEntity>): List<DownloadEntity> {
    val item = track.mediaSource.item ?: return listOf(track)
    val folder = AudiobookEntryBuilder.parentDirOf(item) ?: return listOf(track)
    val album = AudiobookEntryBuilder.albumKeyOf(item)
    return (downloads + track).distinct()
        .filter { download ->
            val other = download.mediaSource.item ?: return@filter false
            AudiobookEntryBuilder.parentDirOf(other) == folder && AudiobookEntryBuilder.albumKeyOf(other) == album
        }
        .sortedWith(compareBy(AudiobookEntryBuilder.trackOrder) { it.mediaSource.item!! })
}

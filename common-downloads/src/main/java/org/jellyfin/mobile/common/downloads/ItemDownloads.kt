package org.jellyfin.mobile.common.downloads

import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** One item's file on the device, in whatever state fetching it has reached. */
data class ItemDownload(val id: String, val state: State, val bytesDownloaded: Long = 0L) {
    enum class State {
        QUEUED,
        DOWNLOADING,

        /** Paused until its requirements are met, e.g. Wi-Fi when downloads are Wi-Fi only. */
        WAITING,
        DONE,
        FAILED,
    }
}

/**
 * Fetches items to the device as plain files, one per item id. Knows nothing about what an item is
 * (a song, an audiobook chapter, a book) or which albums or books group them.
 */
interface ItemDownloads {
    /** Every item it holds or is fetching, by id. */
    val downloads: StateFlow<Map<String, ItemDownload>>

    /** False until [downloads] reflects what's stored; at launch it is read in the background. */
    val isReady: StateFlow<Boolean>

    /** Fetches [id] from [uri], replacing any failed attempt. */
    fun download(id: String, uri: String)

    fun remove(id: String)

    /** The finished file for [id], or null if it isn't fully downloaded. */
    fun fileFor(id: String): File?
}

package org.jellyfin.mobile.music.downloads

import kotlinx.coroutines.flow.StateFlow

/** One song's file on the device, in whatever state fetching it has reached. */
data class TrackDownload(val trackId: String, val state: State, val bytesDownloaded: Long = 0L) {
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
 * Fetches songs to the device, one file per song keyed by its id, so playback finds a download by
 * id whatever URL it would otherwise stream from. Knows nothing about albums or playlists.
 */
interface TrackDownloader {
    /** Every song it holds or is fetching, by id. */
    val downloads: StateFlow<Map<String, TrackDownload>>

    /** False until [downloads] reflects what's stored; at launch it is read in the background. */
    val isReady: StateFlow<Boolean>

    /** Fetches [trackId] from [uri], replacing any failed attempt. */
    fun download(trackId: String, uri: String)

    fun remove(trackId: String)
}

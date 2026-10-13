package org.jellyfin.mobile.music.downloads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Records what it's asked to fetch; tests move each track through its states by hand. */
class FakeTrackDownloader : TrackDownloader {
    override val downloads = MutableStateFlow<Map<String, TrackDownload>>(emptyMap())
    override val isReady = MutableStateFlow(true)
    val requests = mutableListOf<String>()
    val removals = mutableListOf<String>()

    override fun download(trackId: String, uri: String) {
        requests += "$trackId <- $uri"
        set(trackId, TrackDownload.State.QUEUED)
    }

    override fun remove(trackId: String) {
        removals += trackId
        downloads.update { it - trackId }
    }

    fun set(trackId: String, state: TrackDownload.State, bytes: Long = 0L) {
        downloads.update { it + (trackId to TrackDownload(trackId, state, bytes)) }
    }

    fun complete(vararg trackIds: String, bytes: Long = 1_000L) = trackIds.forEach {
        set(it, TrackDownload.State.DONE, bytes)
    }

    /** As if the app's storage was cleared behind the downloader's back. */
    fun lose(trackId: String) {
        downloads.update { it - trackId }
    }
}

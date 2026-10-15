package org.jellyfin.mobile.testing.downloads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.mobile.common.downloads.ItemDownload
import org.jellyfin.mobile.common.downloads.ItemDownloads
import java.io.File

/** Records what it's asked to fetch; tests move each item through its states by hand. */
class FakeItemDownloads : ItemDownloads {
    override val downloads = MutableStateFlow<Map<String, ItemDownload>>(emptyMap())
    override val isReady = MutableStateFlow(true)
    val requests = mutableListOf<String>()
    val removals = mutableListOf<String>()

    override fun download(id: String, uri: String) {
        requests += "$id <- $uri"
        set(id, ItemDownload.State.QUEUED)
    }

    override fun remove(id: String) {
        removals += id
        downloads.update { it - id }
    }

    override fun fileFor(id: String): File? =
        if (downloads.value[id]?.state == ItemDownload.State.DONE) File("/downloads/$id") else null

    fun set(id: String, state: ItemDownload.State, bytes: Long = 0L) {
        downloads.update { it + (id to ItemDownload(id, state, bytes)) }
    }

    fun complete(vararg ids: String, bytes: Long = 1_000L) = ids.forEach { set(it, ItemDownload.State.DONE, bytes) }

    /** As if the app's storage was cleared behind the downloader's back. */
    fun lose(id: String) {
        downloads.update { it - id }
    }
}

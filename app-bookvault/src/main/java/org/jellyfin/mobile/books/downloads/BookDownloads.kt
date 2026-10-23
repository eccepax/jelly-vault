package org.jellyfin.mobile.books.downloads

import kotlinx.coroutines.flow.Flow
import org.jellyfin.mobile.common.downloads.DownloadGroups
import org.jellyfin.mobile.common.downloads.GroupStatus

/**
 * Books kept on the device for reading offline. Each is a download group of one item, its file, so
 * books download in the background like the other apps' downloads (see [DownloadGroups]).
 */
class BookDownloads(private val groups: DownloadGroups) {
    /** Books whose file is fully on the device. */
    val downloadedIds: Flow<Set<String>> = groups.downloadedItemIds

    /** How much space the books' files take. */
    val bytesUsed: Flow<Long> = groups.bytesUsed

    /** Whether any book is downloaded or on its way, even one waiting for Wi-Fi. */
    val hasDownloads: Flow<Boolean> = groups.hasDownloads

    fun status(bookId: String): Flow<GroupStatus> = groups.status(bookId)

    /** Downloads [bookId], or tries again if an earlier download failed. */
    suspend fun download(bookId: String) {
        groups.save(bookId, listOf(bookId))
        groups.retry(bookId)
    }

    suspend fun remove(bookId: String) = groups.remove(bookId)

    /** Deletes every downloaded book. */
    suspend fun removeAll() = groups.removeAll()

    /** At launch: fetches again any downloaded book whose file is gone. */
    suspend fun repair() = groups.repair()
}

package org.jellyfin.mobile.downloads

/**
 * Tracks how many chapter/track downloads are currently in flight per book (keyed by the book's
 * display title, which every track in the same download batch shares — see
 * [org.jellyfin.mobile.utils.requestDownload]), so [JellyfinDownloadService] can post exactly one
 * "Downloaded" notification per book instead of one per chapter file completing.
 *
 * Each track is also remembered by its own [contentId] so [trackFinished] can look up which book
 * it belongs to — the per-track notification data ([androidx.media3.exoplayer.offline.DownloadRequest.data])
 * still carries the *track's own* name, since that's what the ongoing per-line progress
 * notification shows while multiple chapters download in parallel; only the completion path needs
 * to resolve back to the book.
 *
 * A single-track download (a standalone [org.jellyfin.mobile.library.LibraryItem.AudioFile]) is
 * just a batch of size one, so it still resolves — and notifies — immediately: no special-casing
 * needed for the non-audiobook case.
 */
object DownloadBatchTracker {
    private data class BatchState(val remainingTracks: Int, val totalBytes: Long)

    private val lock = Any()
    private val batches = HashMap<String, BatchState>()
    private val bookTitleByContentId = HashMap<String, String>()

    /** Call once a track's download actually starts (not on a remove-existing-download toggle). */
    fun trackStarted(bookTitle: String, contentId: String) {
        synchronized(lock) {
            val current = batches[bookTitle] ?: BatchState(0, 0L)
            batches[bookTitle] = current.copy(remainingTracks = current.remainingTracks + 1)
            bookTitleByContentId[contentId] = bookTitle
        }
    }

    /**
     * Call once the track identified by [contentId] reaches a terminal state (completed or
     * failed). Returns the book's title and its total downloaded bytes exactly once — when this
     * was the last outstanding track for that book — so the caller knows it's safe to post the
     * book-level notification now. Returns null for every earlier track in the batch, and for an
     * untracked [contentId].
     */
    fun trackFinished(contentId: String, bytesDownloaded: Long): Pair<String, Long>? = synchronized(lock) {
        val bookTitle = bookTitleByContentId.remove(contentId) ?: return null
        val current = batches[bookTitle] ?: return null
        val updated = current.copy(
            remainingTracks = current.remainingTracks - 1,
            totalBytes = current.totalBytes + bytesDownloaded,
        )
        if (updated.remainingTracks <= 0) {
            batches.remove(bookTitle)
            bookTitle to updated.totalBytes
        } else {
            batches[bookTitle] = updated
            null
        }
    }
}

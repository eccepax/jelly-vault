package org.jellyfin.mobile.books.downloads

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.common.downloads.FileDownloader
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.jellyfin.mobile.common.downloads.downloadFileName
import java.io.File

/**
 * Where the reader gets a book's file: its download if the book is downloaded, otherwise a copy
 * fetched into [cacheDir] for reading. That copy is fetched like a download (see [FileDownloader]):
 * a fetch cut off part way is never opened as the book, and the next try carries on from there.
 */
@OptIn(UnstableApi::class)
class BookFiles(
    private val downloads: ItemDownloads,
    private val cacheDir: File,
    /** Fetches from the server (adding the access token is up to it). */
    private val dataSourceFactory: DataSource.Factory,
    private val uriOf: (bookId: String) -> String,
) {
    /** [bookId]'s whole file; throws an IOException if it can't be fetched. */
    suspend fun fileFor(bookId: String): File = downloads.fileFor(bookId) ?: withContext(Dispatchers.IO) {
        val copy = File(File(cacheDir, "books"), downloadFileName(bookId))
        FileDownloader(Uri.parse(uriOf(bookId)), copy, dataSourceFactory.createDataSource()).download(null)
        copy
    }
}

package org.jellyfin.mobile.books.downloads

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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
    private val copies = File(cacheDir, "books")
    private val copiesChanged = MutableStateFlow(0)

    /** Space the copies fetched for reading take, so Settings counts them with the downloads. */
    val copiesBytes: Flow<Long> = copiesChanged.map {
        withContext(Dispatchers.IO) { copies.walk().filter { it.isFile }.sumOf { it.length() } }
    }

    /** [bookId]'s whole file; throws an IOException if it can't be fetched. */
    suspend fun fileFor(bookId: String): File = downloads.fileFor(bookId) ?: withContext(Dispatchers.IO) {
        val copy = File(copies, downloadFileName(bookId))
        try {
            FileDownloader(Uri.parse(uriOf(bookId)), copy, dataSourceFactory.createDataSource()).download(null)
        } finally {
            copiesChanged.update { it + 1 }
        }
        copy
    }

    /** Deletes every copy fetched for reading; a book opened again is fetched again. */
    suspend fun removeCopies() {
        withContext(Dispatchers.IO) { copies.deleteRecursively() }
        copiesChanged.update { it + 1 }
    }
}

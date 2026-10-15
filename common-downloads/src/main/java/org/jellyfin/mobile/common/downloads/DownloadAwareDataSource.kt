package org.jellyfin.mobile.common.downloads

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.File

/**
 * A player's data source: an item fully downloaded is read from its file, anything else from the
 * server. Only complete downloads count: reading a partial file and streaming the rest could splice
 * two different encodings (download vs streaming quality) into one corrupt stream.
 */
@UnstableApi
class DownloadAwareDataSource private constructor(
    /** The downloadable item a URL fetches, or null for anything else (artwork, subtitles...). */
    private val itemIdOf: (Uri) -> String?,
    /** An item's finished download, or null. */
    private val fileFor: (itemId: String) -> File?,
    private val onDevice: DataSource,
    private val server: DataSource,
) : DataSource {

    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        onDevice.addTransferListener(transferListener)
        server.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val file = itemIdOf(dataSpec.uri)?.let(fileFor)
        val source = if (file != null) onDevice else server
        current = source
        return source.open(if (file != null) dataSpec.withUri(Uri.fromFile(file)) else dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(current) { "read before open" }.read(buffer, offset, length)

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders.orEmpty()

    override fun close() {
        try {
            current?.close()
        } finally {
            current = null
        }
    }

    class Factory(
        private val itemIdOf: (Uri) -> String?,
        private val fileFor: (itemId: String) -> File?,
        private val onDevice: DataSource.Factory,
        private val server: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            DownloadAwareDataSource(itemIdOf, fileFor, onDevice.createDataSource(), server.createDataSource())
    }
}

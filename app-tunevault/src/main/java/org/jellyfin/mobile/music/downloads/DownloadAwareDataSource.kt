package org.jellyfin.mobile.music.downloads

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * The player's data source: a song fully downloaded is read from the device, anything else from
 * the server. Only complete downloads count: reading a partial file and streaming the rest could
 * splice two different encodings (download vs streaming quality) into one corrupt stream.
 */
@UnstableApi
class DownloadAwareDataSource private constructor(
    private val isDownloaded: (trackId: String) -> Boolean,
    private val onDevice: DataSource,
    private val server: DataSource,
) : DataSource {

    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        onDevice.addTransferListener(transferListener)
        server.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val trackId = TrackCacheKey.trackIdOf(dataSpec.uri)
        val source = if (trackId != null && isDownloaded(trackId)) onDevice else server
        current = source
        return source.open(dataSpec)
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
        private val isDownloaded: (trackId: String) -> Boolean,
        private val onDevice: DataSource.Factory,
        private val server: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            DownloadAwareDataSource(isDownloaded, onDevice.createDataSource(), server.createDataSource())
    }
}

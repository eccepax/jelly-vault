package org.jellyfin.mobile.common.downloads

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.offline.Downloader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Fetches one item to a plain file, for Media3's DownloadManager to run. Bytes go to a `.part` file
 * that is renamed to [target] only once complete, so a file at [target] is always whole. An
 * interrupted download continues from the `.part` file next time, using an HTTP range request.
 */
@UnstableApi
class FileDownloader(
    private val uri: Uri,
    private val target: File,
    private val dataSource: DataSource,
) : Downloader {

    private val partial = File(target.parentFile, target.name + PARTIAL_SUFFIX)

    @Volatile
    private var cancelled = false

    override fun download(progressListener: Downloader.ProgressListener?) {
        if (target.exists()) {
            progressListener?.onProgress(target.length(), target.length(), FULL)
            return
        }
        target.parentFile?.mkdirs()
        val resumeFrom = partial.length()
        val total = try {
            fetchInto(resumeFrom, progressListener)
        } finally {
            dataSource.close()
        }
        if (total != C.LENGTH_UNSET.toLong() && partial.length() != total) {
            throw IOException("Expected $total bytes for $uri, got ${partial.length()}")
        }
        if (!partial.renameTo(target)) throw IOException("Couldn't move the finished download to $target")
        progressListener?.onProgress(target.length(), target.length(), FULL)
    }

    /** Appends the rest of the item to [partial]; returns the item's full length, if the server said. */
    private fun fetchInto(resumeFrom: Long, progressListener: Downloader.ProgressListener?): Long {
        val remaining = dataSource.open(DataSpec.Builder().setUri(uri).setPosition(resumeFrom).build())
        val total = if (remaining == C.LENGTH_UNSET.toLong()) remaining else resumeFrom + remaining
        var downloaded = resumeFrom
        FileOutputStream(partial, true).use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                if (cancelled) throw InterruptedException("Download of $uri cancelled")
                val read = dataSource.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                output.write(buffer, 0, read)
                downloaded += read
                progressListener?.onProgress(total, downloaded, percentOf(downloaded, total))
            }
        }
        return total
    }

    override fun cancel() {
        cancelled = true
    }

    override fun remove() {
        target.delete()
        partial.delete()
    }

    private fun percentOf(downloaded: Long, total: Long): Float =
        if (total <= 0) C.PERCENTAGE_UNSET.toFloat() else downloaded * FULL / total

    private companion object {
        const val PARTIAL_SUFFIX = ".part"
        const val BUFFER_SIZE = 64 * 1024
        const val FULL = 100f
    }
}

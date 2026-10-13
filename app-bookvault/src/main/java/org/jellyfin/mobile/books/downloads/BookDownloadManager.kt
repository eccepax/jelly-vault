package org.jellyfin.mobile.books.downloads

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.mobile.common.data.dao.DownloadRecordDao
import org.jellyfin.mobile.common.data.entity.DownloadRecord
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber
import java.io.File

class BookDownloadManager(
    private val context: Context,
    private val apiClient: ApiClient,
    private val httpClient: OkHttpClient,
    private val downloadRecordDao: DownloadRecordDao,
) {
    private fun getDownloadDir() = File(context.filesDir, "books").also { it.mkdirs() }

    fun getDownloadedFile(bookItemId: String): File? =
        File(getDownloadDir(), bookItemId).takeIf { it.exists() }

    suspend fun downloadBook(book: BaseItemDto) = withContext(Dispatchers.IO) {
        val itemId = book.id?.toString() ?: return@withContext
        val file = File(getDownloadDir(), itemId)
        if (!file.exists()) {
            val baseUrl = requireNotNull(apiClient.baseUrl) { "No base URL" }.trimEnd('/')
            val token = apiClient.accessToken ?: ""
            val url = "$baseUrl/Items/$itemId/Download?api_key=$token"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Download failed: ${response.code}" }
                response.body!!.byteStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        downloadRecordDao.insert(
            DownloadRecord(
                itemId = itemId,
                title = book.name ?: "",
                filePath = file.absolutePath,
                fileSizeBytes = file.length(),
                downloadedAt = System.currentTimeMillis(),
                mediaType = "BOOK",
            ),
        )
        Timber.d("Book downloaded: ${book.name}")
    }

    suspend fun deleteDownload(itemId: String) = withContext(Dispatchers.IO) {
        File(getDownloadDir(), itemId).delete()
        downloadRecordDao.delete(itemId)
    }
}

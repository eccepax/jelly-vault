package org.jellyfin.mobile.books.downloads

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jellyfin.mobile.books.data.BookDatabase
import org.jellyfin.mobile.common.downloads.DownloadFiles
import org.jellyfin.mobile.common.downloads.DownloadGroups
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.jellyfin.mobile.common.downloads.Media3ItemDownloads
import org.jellyfin.mobile.common.downloads.fileDownloadManager
import org.jellyfin.mobile.common.server.itemDownloadUrl
import org.jellyfin.mobile.common.server.withAccessToken
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.serializer.toUUID
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import timber.log.Timber
import java.io.File

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 60_000

/** Where to fetch a book's file; the data source adds the access token. */
private fun ApiClient.bookUri(bookId: String): String =
    itemDownloadUrl(requireNotNull(baseUrl) { "No server" }, bookId.toUUID(), token = null)

/** Where books were kept before downloads moved to the shared engine; nothing reads it now. */
private fun Context.deleteOldBookDownloads() {
    File(filesDir, "books").takeIf { it.isDirectory }?.deleteRecursively()
}

@UnstableApi
val bookDownloadsModule = module {
    single<DatabaseProvider> { StandaloneDatabaseProvider(androidContext()) }
    single { DownloadFiles(File(androidContext().filesDir, "downloaded")) }
    // Fetches from the Vault, adding the access token to each request
    single<DataSource.Factory> {
        val apiClient = get<ApiClient>()
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
        ResolvingDataSource.Factory(http) { dataSpec ->
            dataSpec.withUri(Uri.parse(withAccessToken(dataSpec.uri.toString(), apiClient.accessToken)))
        }
    }
    single {
        androidContext().deleteOldBookDownloads()
        fileDownloadManager(androidContext(), get(), get(), get())
    }
    single<ItemDownloads> {
        val context = androidContext()
        Media3ItemDownloads(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Main)) {
            try {
                DownloadService.start(context, BookDownloadService::class.java)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Not allowed from the background; the manager still downloads while the app runs.
                Timber.w(e, "Couldn't start the download service")
            }
        }
    }
    single {
        val apiClient = get<ApiClient>()
        DownloadGroups(get(), get<BookDatabase>().downloadGroupDao, uriOf = apiClient::bookUri)
    }
    single { BookDownloads(get()) }
    single {
        val apiClient = get<ApiClient>()
        BookFiles(get(), androidContext().cacheDir, get(), uriOf = apiClient::bookUri)
    }
}

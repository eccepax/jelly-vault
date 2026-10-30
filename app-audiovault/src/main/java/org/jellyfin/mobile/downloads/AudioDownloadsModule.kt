package org.jellyfin.mobile.downloads

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.cast.Casting
import org.jellyfin.mobile.common.account.DownloadsOnSignOut
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.downloads.DownloadFiles
import org.jellyfin.mobile.common.downloads.DownloadGroups
import org.jellyfin.mobile.common.downloads.DownloadSettings
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.jellyfin.mobile.common.downloads.Media3DownloadSettings
import org.jellyfin.mobile.common.downloads.Media3ItemDownloads
import org.jellyfin.mobile.common.downloads.downloadRequirements
import org.jellyfin.mobile.common.downloads.fileDownloadManager
import org.jellyfin.mobile.common.playback.SleepTimer
import org.jellyfin.mobile.common.server.JellyfinServerPositions
import org.jellyfin.mobile.common.server.itemDownloadUrl
import org.jellyfin.mobile.data.JellyfinDatabase
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.playback.AudiobookProgressReporter
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.settings.audioVaultSignOut
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.serializer.toUUID
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import timber.log.Timber
import java.io.File

/** Where a track's file is fetched from; the data source adds the authorization. */
private fun ApiClient.trackUri(trackId: String): String =
    itemDownloadUrl(requireNotNull(baseUrl) { "No server" }, trackId.toUUID(), token = null)

/** Where tracks were kept before downloads moved to the shared engine; nothing reads it now. */
private fun Context.deleteOldTrackCache() {
    File(filesDir, "MediaCache").takeIf { it.isDirectory }?.deleteRecursively()
    File(filesDir, "Downloads").takeIf { it.isDirectory }?.deleteRecursively()
}

/** How long signing out waits for listening to reach the server, as it does for ending the session. */
private const val SEND_LISTENING_ON_SIGN_OUT_MS = 5_000L

/**
 * What can be sent in time, without holding up signing out or stopping it: a slow or unreachable
 * server, or a failure, leaves the rest to be forgotten with the account.
 */
private suspend fun OfflineListeningSync.sendBeforeSigningOut() {
    try {
        withTimeoutOrNull(SEND_LISTENING_ON_SIGN_OUT_MS) { sync() }
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Timber.w(e, "Couldn't send the listening before signing out")
    }
}

@UnstableApi
val audioDownloadsModule = module {
    single<DatabaseProvider> { StandaloneDatabaseProvider(androidContext()) }
    single { DownloadFiles(File(androidContext().filesDir, "downloaded")) }
    single {
        androidContext().deleteOldTrackCache()
        fileDownloadManager(
            androidContext(),
            get(),
            get<DataSource.Factory>(),
            get(),
            requirements = downloadRequirements(get<AudioVaultAppPreferences>().downloadOnWifiOnly),
        )
    }
    single<ItemDownloads> {
        val context = androidContext()
        Media3ItemDownloads(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Main)) {
            try {
                DownloadService.start(context, AudioDownloadService::class.java)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Not allowed from the background; the manager still downloads while the app runs.
                Timber.w(e, "Couldn't start the download service")
            }
        }
    }
    single {
        val apiClient = get<ApiClient>()
        DownloadGroups(get(), get<JellyfinDatabase>().downloadGroupDao, uriOf = apiClient::trackUri)
    }
    single {
        val apiClient = get<ApiClient>()
        val resolver = get<MediaSourceResolver>()
        val profile = get<DeviceProfileBuilder>().getDeviceProfile()
        val files = get<DownloadFiles>()
        AudiobookDownloads(get(), get<JellyfinDatabase>().downloadDao) { trackId ->
            val id = trackId.toUUID()
            resolver.resolveMediaSource(itemId = id, mediaSourceId = trackId.replace("-", ""), deviceProfile = profile)
                .onFailure { Timber.w(it, "Couldn't load the details of %s", trackId) }
                .getOrNull()
                ?.let { source ->
                    DownloadEntity(LocalJellyfinMediaSource(source, files.root.path, apiClient.trackUri(trackId), 0))
                }
        }
    }
    // What Settings shows and changes: Wi-Fi only, the space downloads take, and removing them all
    single<DownloadSettings> {
        val downloads = get<AudiobookDownloads>()
        Media3DownloadSettings(
            manager = get(),
            savedWifiOnly = get<AudioVaultAppPreferences>()::downloadOnWifiOnly,
            bytesUsed = downloads.bytesUsed,
            hasDownloads = downloads.hasDownloads,
            removeAll = downloads::removeAll,
        )
    }
    // Listening saved on the device reaches the server once it can be reached
    single {
        OfflineListeningSync(
            get<JellyfinDatabase>().downloadDao,
            JellyfinServerPositions(get()),
            isKept = get<AudiobookDownloads>()::isKept,
        )
    }
    // Signing out keeps downloads unless the user asked otherwise in Settings
    single {
        val downloads = get<AudiobookDownloads>()
        val downloadsOnSignOut = DownloadsOnSignOut(get(), downloads::removeAll)
        val sleepTimer = get<SleepTimer>()
        val reporter = get<AudiobookProgressReporter>()
        val casting = get<Casting>()
        val offlineListening = get<OfflineListeningSync>()
        audioVaultSignOut(
            session = get(),
            logout = get<ApiClientController>()::logoutUser,
            stopPlayback = {
                sleepTimer.cancel()
                reporter.finishAndStop()
                offlineListening.sendBeforeSigningOut()
            },
            forgetCast = casting::forgetCast,
            forgetDownloads = {
                // Listening that couldn't be sent by now is the account's, not the next one's
                downloads.forgetUnsentListening()
                downloadsOnSignOut()
            },
        )
    }
}

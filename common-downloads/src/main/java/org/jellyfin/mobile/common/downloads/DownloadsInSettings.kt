package org.jellyfin.mobile.common.downloads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/** What Settings shows of downloads: the space they take, and whether there's anything to remove. */
data class DownloadSpace(val bytes: Long, val hasDownloads: Boolean)

/**
 * The downloads part of a settings screen, in [scope] (the screen's view model's, which outlives
 * the screen): the [space] downloads take, and removing them all.
 */
class DownloadsInSettings(private val scope: CoroutineScope, private val settings: DownloadSettings) {

    /**
     * Followed only while a screen shows it: downloads in progress change it often. Null until the
     * downloads say, so the screen doesn't first claim there's nothing downloaded.
     */
    val space: StateFlow<DownloadSpace?> = combine(settings.bytesUsed, settings.hasDownloads, ::DownloadSpace)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_FOLLOWING_AFTER_MS), null)

    private val _removeFailures = Channel<Unit>(Channel.CONFLATED)

    /** Each time removing them all failed, to say so; it can be tried again. */
    val removeFailures: Flow<Unit> = _removeFailures.receiveAsFlow()

    fun removeAll() {
        scope.launch {
            try {
                settings.removeAll()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Couldn't remove the downloads")
                _removeFailures.send(Unit)
            }
        }
    }

    companion object {
        /** Long enough that a rotation doesn't stop and restart following. */
        const val STOP_FOLLOWING_AFTER_MS = 5_000L
    }
}

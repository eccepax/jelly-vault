package org.jellyfin.mobile.stats

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.dao.FinishedBookDao
import org.jellyfin.mobile.data.dao.ListeningDao
import org.jellyfin.mobile.data.entity.FinishedBookEntity
import org.jellyfin.mobile.player.playback.AudiobookPlaybackController
import org.jellyfin.mobile.player.playback.AudiobookPlaybackState
import java.time.LocalDate

/** Where [ListeningLog] keeps what it sees. */
interface ListeningStore {
    suspend fun addListened(day: LocalDate, ms: Long)
    suspend fun bookFinished(book: FinishedBookEntity)
}

/** The device's database as a [ListeningStore]. */
class DatabaseListeningStore(private val days: ListeningDao, private val books: FinishedBookDao) : ListeningStore {
    override suspend fun addListened(day: LocalDate, ms: Long) = days.addListened(day.toString(), ms)
    override suspend fun bookFinished(book: FinishedBookEntity) = books.bookFinished(book)
}

/**
 * Keeps the listening stats: the time sound plays (on this device or a cast device, at whatever
 * speed: the listener's time), by [today]'s date, saved every [SAVE_EVERY_MS] and whenever it stops;
 * and each book played to the end of its last part, by its first track. App-wide, like the player.
 */
class ListeningLog(
    private val controller: AudiobookPlaybackController,
    private val store: ListeningStore,
    private val today: () -> LocalDate = LocalDate::now,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var unsavedMs = 0L
    private var unsavedDay: LocalDate? = null

    /** The book last seen finished, so it isn't counted again while it sits at its end. */
    private var finishedBook: String? = null

    fun start(scope: CoroutineScope): Job = scope.launch {
        launch { controller.state.collect(::onState) }
        while (true) {
            delay(TICK_MS)
            onTick()
        }
    }

    private suspend fun onTick() {
        val state = controller.state.value
        if (!state.isPlaying || state.currentTrack == null) return
        val day = today()
        if (unsavedDay != null && unsavedDay != day) save()
        unsavedDay = day
        unsavedMs += TICK_MS
        if (unsavedMs >= SAVE_EVERY_MS) save()
    }

    private suspend fun onState(state: AudiobookPlaybackState) {
        if (!state.isPlaying) save()
        val track = state.currentTrack
        if (state.isEnded && !state.hasNext && track != null) {
            val book = state.tracks.first().id
            if (book != finishedBook) {
                finishedBook = book
                store.bookFinished(FinishedBookEntity(book, track.bookTitle ?: track.title, now()))
            }
        } else if (!state.isEnded) {
            finishedBook = null
        }
    }

    private suspend fun save() {
        val day = unsavedDay ?: return
        val ms = unsavedMs
        unsavedMs = 0L
        unsavedDay = null
        if (ms > 0) store.addListened(day, ms)
    }

    companion object {
        const val TICK_MS = 1_000L
        const val SAVE_EVERY_MS = 30_000L
    }
}

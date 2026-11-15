package org.jellyfin.mobile.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.jellyfin.mobile.data.dao.FinishedBookDao
import org.jellyfin.mobile.data.dao.ListeningDao
import java.time.LocalDate

/** The listening stats, from what's kept on the device (see [ListeningLog]). */
class StatsViewModel(
    days: ListeningDao,
    books: FinishedBookDao,
    today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    val stats: StateFlow<ListeningStats> = combine(days.days(), books.finishedBooks()) { listened, finished ->
        listeningStatsOf(listened, finished, today())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ListeningStats())
}

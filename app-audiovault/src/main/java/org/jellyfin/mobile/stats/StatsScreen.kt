package org.jellyfin.mobile.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.components.EmptyState
import org.jellyfin.mobile.common.ui.components.SecondaryText
import org.jellyfin.mobile.common.ui.components.SectionTitle
import org.jellyfin.mobile.common.ui.components.VaultTopBar
import org.jellyfin.mobile.common.ui.theme.VaultSpacing
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import org.jellyfin.mobile.common.R as CommonR

@Composable
fun StatsRoute(onBack: () -> Unit, viewModel: StatsViewModel = koinViewModel()) {
    val stats by viewModel.stats.collectAsState()
    StatsScreen(stats, onBack)
}

/** Today's listening, the last seven days' (with a bar each), all of it, and the books finished. */
@Composable
fun StatsScreen(stats: ListeningStats, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            VaultTopBar(
                title = stringResource(R.string.listening_stats),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CommonR.string.vault_back),
                        )
                    }
                },
            )
            if (stats.allTimeMs == 0L && stats.booksFinished == 0) {
                EmptyState(stringResource(R.string.stats_nothing_yet), icon = Icons.Filled.BarChart)
                return@Column
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = VaultSpacing.Large)) {
                Row(modifier = Modifier.padding(horizontal = VaultSpacing.Large)) {
                    Figure(stringResource(R.string.stats_today), formatListened(stats.todayMs), Modifier.weight(1f))
                    Figure(
                        stringResource(R.string.stats_last_seven_days),
                        formatListened(stats.lastSevenDaysMs),
                        Modifier.weight(1f),
                    )
                }
                Row(modifier = Modifier.padding(horizontal = VaultSpacing.Large, vertical = VaultSpacing.Medium)) {
                    Figure(
                        stringResource(R.string.stats_all_time),
                        formatListened(stats.allTimeMs),
                        Modifier.weight(1f),
                    )
                    Figure(
                        stringResource(R.string.stats_books_finished),
                        stats.booksFinished.toString(),
                        Modifier.weight(1f),
                    )
                }
                SectionTitle(stringResource(R.string.stats_last_seven_days))
                WeekChart(stats.chart)
                if (stats.recentlyFinished.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.stats_recently_finished))
                    stats.recentlyFinished.forEach { book ->
                        Text(
                            book.title,
                            style = MaterialTheme.typography.body1,
                            modifier = Modifier.padding(
                                horizontal = VaultSpacing.Large,
                                vertical = VaultSpacing.XSmall,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** One figure: what it is, and how much. */
@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SecondaryText(label)
        Text(value, style = MaterialTheme.typography.h6)
    }
}

/** A bar for each of the last seven days, as tall as its listening against the most in a day. */
@Composable
private fun WeekChart(chart: List<Pair<LocalDate, Long>>) {
    val most = chart.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1L
    Row(
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.Small),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT).padding(horizontal = VaultSpacing.Large),
    ) {
        chart.forEach { (day, ms) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction = (ms.toFloat() / most).coerceAtLeast(MIN_BAR))
                            .background(
                                MaterialTheme.colors.primary,
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                            ),
                    )
                }
                Spacer(Modifier.height(VaultSpacing.XSmall))
                SecondaryText(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
            }
        }
    }
}

private val CHART_HEIGHT = 140.dp

/** A day not listened still shows a sliver, so the chart reads as seven days. */
private const val MIN_BAR = 0.02f

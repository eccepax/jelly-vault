package org.jellyfin.mobile.stats

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.data.entity.FinishedBookEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import org.jellyfin.mobile.common.R as CommonR

/** The listening stats page. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StatsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 15)
    private val hour = 3_600_000L
    private var backs = 0

    private fun string(resId: Int) = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    private fun show(stats: ListeningStats) {
        composeRule.setContent { AppTheme(VaultBrand.AudioVault) { StatsScreen(stats, onBack = { backs++ }) } }
    }

    @Test
    fun `it shows today's listening, the last seven days', all of it, and the books finished`() {
        show(
            ListeningStats(
                todayMs = hour + 20 * 60_000L,
                lastSevenDaysMs = 5 * hour,
                allTimeMs = 42 * hour,
                chart = (6 downTo 0).map { today.minusDays(it.toLong()) to it * hour },
                booksFinished = 3,
                recentlyFinished = listOf(FinishedBookEntity("d", "Dune", finishedAt = 0L)),
            ),
        )

        composeRule.onNodeWithText("1 h 20 min").assertExists()
        composeRule.onNodeWithText("5 h").assertExists()
        composeRule.onNodeWithText("42 h").assertExists()
        composeRule.onNodeWithText("3").assertExists()
        composeRule.onNodeWithText("Dune").assertExists()
    }

    @Test
    fun `with nothing listened yet, it says so`() {
        show(ListeningStats())

        composeRule.onNodeWithText(string(R.string.stats_nothing_yet)).assertExists()
    }

    @Test
    fun `back goes back`() {
        show(ListeningStats())

        composeRule.onNodeWithContentDescription(string(CommonR.string.vault_back)).performClick()

        backs shouldBe 1
    }
}

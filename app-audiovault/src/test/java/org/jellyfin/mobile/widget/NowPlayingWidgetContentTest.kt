package org.jellyfin.mobile.widget

import android.app.Application
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.jellyfin.mobile.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.jellyfin.mobile.common.R as CommonR

/** The widget as it's drawn: the book playing with its buttons, or that nothing is. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NowPlayingWidgetContentTest {

    private fun string(resId: Int) = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    private val labels = WidgetLabels.of(ApplicationProvider.getApplicationContext())

    @Test
    fun `while a book plays, it shows the book and its part, with back and pause`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 80.dp))
        provideComposable {
            NowPlayingContent(
                NowPlaying(title = "Dune", detail = "Chapter 3", isPlaying = true),
                labels,
            )
        }

        onNode(hasText("Dune")).assertExists()
        onNode(hasText("Chapter 3")).assertExists()
        onNode(hasContentDescriptionEqualTo(string(CommonR.string.vault_pause))).assertExists()
        onNode(hasContentDescriptionEqualTo(string(R.string.player_rewind))).assertExists()
    }

    @Test
    fun `paused, it offers to play`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 80.dp))
        provideComposable { NowPlayingContent(NowPlaying(title = "Dune", detail = null, isPlaying = false), labels) }

        onNode(hasContentDescriptionEqualTo(string(CommonR.string.vault_play))).assertExists()
    }

    @Test
    fun `with nothing playing, it says so`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 80.dp))
        provideComposable { NowPlayingContent(null, labels) }

        onNode(hasText(string(R.string.widget_nothing_playing))).assertExists()
    }
}

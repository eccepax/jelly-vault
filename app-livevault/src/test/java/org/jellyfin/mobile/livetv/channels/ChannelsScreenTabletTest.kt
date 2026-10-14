package org.jellyfin.mobile.livetv.channels

import android.app.Application
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jellyfin.mobile.livetv.catalog.ChannelFilter
import org.jellyfin.mobile.livetv.catalog.channel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Wide screens (tablets, and TVs at 960dp) get a grid of channels rather than one long list. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w1280dp-h800dp")
class ChannelsScreenTabletTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `channels sit side by side`() {
        val first = channel("1", "Bulls")
        val second = channel("2", "Pacers")
        composeRule.setContent {
            ChannelsScreen(
                state = ChannelsUiState.Loaded(listOf(first, second), tags = emptyList(), filter = ChannelFilter()),
                now = Instant.EPOCH,
                onQueryChange = {},
                onTagSelect = {},
                onFavoritesSelect = {},
                onFavoriteToggle = {},
                onChannelClick = {},
                onRetry = {},
            )
        }

        val a = composeRule.onNodeWithTag(ChannelsScreenTags.channel(first.id)).getBoundsInRoot()
        val b = composeRule.onNodeWithTag(ChannelsScreenTags.channel(second.id)).getBoundsInRoot()
        b.top shouldBe a.top
        b.left shouldNotBe a.left
    }
}

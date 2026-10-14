package org.jellyfin.mobile.livetv.channels

import androidx.lifecycle.SavedStateHandle
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.livetv.catalog.ChannelFilter
import org.jellyfin.mobile.livetv.catalog.FakeLiveTvCatalog
import org.jellyfin.mobile.livetv.catalog.channel
import org.jellyfin.mobile.livetv.catalog.program
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelsViewModelTest {

    private val catalog = FakeLiveTvCatalog()
    private val bulls = channel("1", "Bulls", tags = listOf("My Teams", "Sports"), now = program("Bulls at Pacers"))
    private val espn = channel("43", "ESPN", tags = listOf("Sports"))
    private val cnn = channel("200", "CNN", tags = listOf("News"), favorite = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        catalog.channels = listOf(bulls, espn, cnn)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = ChannelsViewModel(catalog, savedState)

    private val ChannelsViewModel.loaded get() = state.value.shouldBeInstanceOf<ChannelsUiState.Loaded>()

    @Test
    fun `shows loading until the catalog answers`() {
        val gate = CompletableDeferred<Unit>()
        catalog.gate = gate

        val viewModel = viewModel()
        viewModel.state.value shouldBe ChannelsUiState.Loading

        gate.complete(Unit)
        viewModel.loaded.channels shouldContainExactly listOf(bulls, espn, cnn)
    }

    @Test
    fun `shows every channel and every tag`() {
        val loaded = viewModel().loaded

        loaded.channels shouldContainExactly listOf(bulls, espn, cnn)
        loaded.tags shouldContainExactly listOf("My Teams", "News", "Sports")
        loaded.filter shouldBe ChannelFilter()
    }

    @Test
    fun `a failed load shows an error`() {
        catalog.failure = IOException("offline")

        viewModel().state.value shouldBe ChannelsUiState.Error
    }

    @Test
    fun `retry after a failure loads the channels`() {
        catalog.failure = IOException("offline")
        val viewModel = viewModel()

        catalog.failure = null
        viewModel.retry()

        viewModel.loaded.channels shouldContainExactly listOf(bulls, espn, cnn)
    }

    @Test
    fun `selecting a tag shows only its channels`() {
        val viewModel = viewModel()

        viewModel.selectTag("Sports")

        viewModel.loaded.channels shouldContainExactly listOf(bulls, espn)
        viewModel.loaded.filter.tag shouldBe "Sports"
    }

    @Test
    fun `selecting all clears the tag`() {
        val viewModel = viewModel()
        viewModel.selectTag("News")

        viewModel.selectTag(null)

        viewModel.loaded.channels shouldContainExactly listOf(bulls, espn, cnn)
    }

    @Test
    fun `favorites shows only favorite channels, in place of a tag`() {
        val viewModel = viewModel()
        viewModel.selectTag("Sports")

        viewModel.showFavorites()

        viewModel.loaded.channels shouldContainExactly listOf(cnn)
        viewModel.loaded.filter shouldBe ChannelFilter(favoritesOnly = true)
    }

    @Test
    fun `selecting a tag leaves favorites`() {
        val viewModel = viewModel()
        viewModel.showFavorites()

        viewModel.selectTag("Sports")

        viewModel.loaded.filter shouldBe ChannelFilter(tag = "Sports")
    }

    @Test
    fun `searching narrows the channels within the selected tag`() {
        val viewModel = viewModel()
        viewModel.selectTag("Sports")

        viewModel.onQueryChange("pacers")

        viewModel.loaded.channels shouldContainExactly listOf(bulls)
        viewModel.loaded.filter.query shouldBe "pacers"
    }

    @Test
    fun `the filter survives the view model being recreated`() {
        val savedState = SavedStateHandle()
        viewModel(savedState).apply {
            selectTag("Sports")
            onQueryChange("esp")
        }

        viewModel(savedState).loaded.filter shouldBe ChannelFilter(tag = "Sports", query = "esp")
    }

    @Test
    fun `favoriting a channel shows at once and saves it on the server`() {
        val viewModel = viewModel()

        viewModel.toggleFavorite(espn.id)

        viewModel.loaded.channels.first { it.id == espn.id }.isFavorite shouldBe true
        catalog.favoriteCalls shouldContainExactly listOf(espn.id to true)
    }

    @Test
    fun `unfavoriting a channel removes it from favorites`() {
        val viewModel = viewModel()
        viewModel.showFavorites()

        viewModel.toggleFavorite(cnn.id)

        viewModel.loaded.channels.shouldBeEmpty()
        catalog.favoriteCalls shouldContainExactly listOf(cnn.id to false)
    }

    @Test
    fun `a favorite the server rejects is put back`() {
        val viewModel = viewModel()
        catalog.failure = IOException("offline")

        viewModel.toggleFavorite(espn.id)

        viewModel.loaded.channels.first { it.id == espn.id }.isFavorite shouldBe false
    }

    @Test
    fun `refresh picks up what is on now without showing loading`() {
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>()
        catalog.gate = gate
        val later = espn.copy(nowPlaying = program("SportsCenter"))
        catalog.channels = listOf(bulls, later, cnn)

        viewModel.refresh()
        viewModel.loaded.channels shouldContainExactly listOf(bulls, espn, cnn)

        gate.complete(Unit)
        viewModel.loaded.channels shouldContainExactly listOf(bulls, later, cnn)
    }

    @Test
    fun `a failed refresh keeps the channels shown`() {
        val viewModel = viewModel()
        catalog.failure = IOException("offline")

        viewModel.refresh()

        viewModel.loaded.channels shouldContainExactly listOf(bulls, espn, cnn)
    }

    @Test
    fun `refresh during the first load does not load again`() {
        val gate = CompletableDeferred<Unit>()
        catalog.gate = gate
        val viewModel = viewModel()

        viewModel.refresh()
        gate.complete(Unit)

        catalog.channelLoads shouldBe 1
    }

    @Test
    fun `a program that has ended makes the channels reload`() {
        val viewModel = viewModel()
        val loadsBefore = catalog.channelLoads

        viewModel.refreshIfEnded(now = Instant.parse("2026-10-14T20:00:00Z"))

        catalog.channelLoads shouldBe loadsBefore + 1
    }

    @Test
    fun `nothing reloads while every program is still on`() {
        val viewModel = viewModel()
        val loadsBefore = catalog.channelLoads

        viewModel.refreshIfEnded(now = Instant.parse("2026-10-14T19:59:00Z"))

        catalog.channelLoads shouldBe loadsBefore
    }

    @Test
    fun `refresh after a failed first load acts as retry`() {
        catalog.failure = IOException("offline")
        val viewModel = viewModel()
        catalog.failure = null

        viewModel.refresh()

        viewModel.loaded.channels shouldContainExactly listOf(bulls, espn, cnn)
    }
}

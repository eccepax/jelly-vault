package org.jellyfin.mobile.widget

import android.content.Context
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The widget is drawn again only when what it shows changes, not at every event of the player. */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingOnWidgetTest {

    private val drawn = mutableListOf<NowPlaying?>()
    private val dune = NowPlaying(title = "Dune", detail = "Chapter 3", isPlaying = true)

    @Test
    fun `only a change is drawn, nothing playing too`() = runTest {
        val widget = NowPlayingOnWidget(mockk<Context>(), backgroundScope) { _, now -> drawn += now }

        widget.show(dune)
        widget.show(dune)
        widget.show(dune.copy(isPlaying = false))
        widget.show(null)
        widget.show(null)
        runCurrent()

        drawn shouldBe listOf(dune, dune.copy(isPlaying = false), null)
    }
}

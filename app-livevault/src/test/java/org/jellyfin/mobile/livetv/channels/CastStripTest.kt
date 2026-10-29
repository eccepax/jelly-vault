package org.jellyfin.mobile.livetv.channels

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.livetv.catalog.channel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The channel left playing on a cast device, along the bottom of the channel list. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class CastStripTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val devils = channel("4.1", "Devils")
    private val events = mutableListOf<String>()

    private fun show(castDevice: String?) {
        composeRule.setContent {
            CastStrip(
                channel = devils,
                castDevice = castDevice,
                onOpen = { events += "open" },
                onStop = { events += "stop" },
            )
        }
    }

    @Test
    fun `says which channel plays on which cast device`() {
        show(castDevice = "Main Bedroom TV")

        composeRule.onNodeWithText(devils.castTitle).assertExists()
        composeRule.onNodeWithText("Playing on Main Bedroom TV").assertExists()
    }

    @Test
    fun `a cast device that hasn't said its name yet is still said to be playing it`() {
        show(castDevice = null)

        composeRule.onNodeWithText("Playing on your cast device").assertExists()
    }

    @Test
    fun `tapping it goes back to the player`() {
        show(castDevice = "Main Bedroom TV")

        composeRule.onNodeWithText(devils.castTitle).performClick()

        events shouldBe listOf("open")
    }

    @Test
    fun `its stop button stops the channel`() {
        show(castDevice = "Main Bedroom TV")

        composeRule.onNodeWithContentDescription("Stop").performClick()

        events shouldBe listOf("stop")
    }
}

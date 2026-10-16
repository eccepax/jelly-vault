package org.jellyfin.mobile.common.ui.components

import android.app.Application
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** How the shared components behave, whichever app they're in. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { AppTheme(VaultBrand.Jellyfin, content) }
    }

    @Test
    fun `tapping anywhere on a switch row flips the setting`() {
        show {
            var on by remember { mutableStateOf(false) }
            SettingsSwitchRow(label = "Download on Wi-Fi only", checked = on, onCheckedChange = { on = it })
        }

        composeRule.onNodeWithText("Download on Wi-Fi only").assertIsOff().performClick()

        composeRule.onNodeWithText("Download on Wi-Fi only").assertIsOn()
    }

    @Test
    fun `a radio group selects the option tapped`() {
        val options = listOf(Choice("low", "Low"), Choice("high", "High", detail = "Uses more data"))
        show {
            var selected by remember { mutableStateOf("low") }
            SettingsRadioGroup(choices = options, selected = selected, onSelect = { selected = it })
        }

        composeRule.onNodeWithText("High").performClick()

        composeRule.onNodeWithText("High").assertIsSelected()
        composeRule.onNodeWithText("Low").assertIsNotSelected()
    }

    @Test
    fun `an inline confirmation acts only on yes`() {
        var confirmed = 0
        var cancelled = 0
        show {
            InlineConfirm(
                message = "Sign out of the Vault?",
                confirmLabel = "Yes, sign out",
                onConfirm = { confirmed++ },
                onCancel = { cancelled++ },
            )
        }

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Yes, sign out").performClick()

        cancelled shouldBe 1
        confirmed shouldBe 1
    }

    @Test
    fun `a choice dialog reports the choice and closes`() {
        var chosen: Int? = null
        show {
            var open by remember { mutableStateOf(true) }
            if (open) {
                ChoiceDialog(
                    title = "Sleep timer",
                    choices = listOf(Choice(15, "15 minutes"), Choice(30, "30 minutes")),
                    selected = null,
                    onSelect = { chosen = it },
                    onDismiss = { open = false },
                )
            }
        }

        composeRule.onNodeWithText("30 minutes").performClick()

        chosen shouldBe 30
        composeRule.onNodeWithText("Sleep timer").assertDoesNotExist()
    }

    @Test
    fun `an error offers a retry`() {
        var retries = 0
        show { ErrorState(message = "Couldn't reach the Vault", onRetry = { retries++ }) }

        composeRule.onNodeWithText("Retry").performClick()

        retries shouldBe 1
    }

    @Test
    fun `the search field offers to clear only once something is typed`() {
        show {
            var query by remember { mutableStateOf("") }
            SearchField(query = query, onQueryChange = { query = it }, placeholder = "Search")
            if (query.isEmpty()) {
                TextButton(onClick = { query = "dune" }) {
                    Text("type")
                }
            }
        }
        composeRule.onNodeWithContentDescription("Clear search").assertDoesNotExist()

        composeRule.onNodeWithText("type").performClick()
        composeRule.onNodeWithContentDescription("Clear search").performClick()

        composeRule.onNodeWithText("dune").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Clear search").assertDoesNotExist()
    }

    @Test
    fun `the bottom bar marks the selected tab`() {
        show {
            var tab by remember { mutableStateOf(0) }
            VaultBottomBar {
                VaultBottomBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = Icons.Filled.Home, label = "Home")
                VaultBottomBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = Icons.Filled.Search,
                    label = "Search",
                )
            }
        }

        composeRule.onNodeWithText("Search").performClick()

        composeRule.onNodeWithText("Home").assertIsNotSelected()
        composeRule.onNodeWithText("Search").assertIsSelected()
    }

    @Test
    fun `a row with a click handler reports the tap`() {
        var taps = 0
        show { MediaListRow(title = "Dune", subtitle = "Frank Herbert", onClick = { taps++ }) }

        composeRule.onNodeWithText("Dune").performClick()

        taps shouldBe 1
    }

    @Test
    fun `a grid card tells a tap from a long press`() {
        val events = mutableListOf<String>()
        show {
            MediaGridCard(
                title = "Dune",
                subtitle = null,
                cover = {},
                onClick = { events += "tap" },
                onLongClick = { events += "long press" },
            )
        }

        composeRule.onNodeWithText("Dune").performClick()
        composeRule.onNodeWithText("Dune").performTouchInput { longClick() }

        events shouldBe listOf("tap", "long press")
    }

    @Test
    fun `a notice closes on OK`() {
        show {
            var open by remember { mutableStateOf(true) }
            if (open) NoticeDialog(message = "No tags yet", onDismiss = { open = false })
        }

        composeRule.onNodeWithText("OK").performClick()

        composeRule.onNodeWithText("No tags yet").assertDoesNotExist()
    }

    @Test
    fun `a text field dialog hands over what was typed`() {
        var entered: String? = null
        show {
            TextFieldDialog(
                title = "Go to page",
                label = "Page number",
                confirmLabel = "Go",
                onConfirm = { entered = it },
                onDismiss = {},
            )
        }

        composeRule.onNodeWithText("Page number").performTextInput("42")
        composeRule.onNodeWithText("Go").performClick()

        entered shouldBe "42"
    }

    @Test
    fun `cancelling a text field dialog hands over nothing`() {
        var entered: String? = null
        var dismissed = false
        show {
            TextFieldDialog(
                title = "Add bookmark",
                label = "Note (optional)",
                confirmLabel = "Add bookmark",
                onConfirm = { entered = it },
                onDismiss = { dismissed = true },
            )
        }

        composeRule.onNodeWithText("Note (optional)").performTextInput("Great line")
        composeRule.onNodeWithText("Cancel").performClick()

        entered shouldBe null
        dismissed shouldBe true
    }

    @Test
    fun `a long list of choices scrolls to the last`() {
        val chapters = (1..60).map { Choice(it, "Chapter $it") }
        show { ChoiceDialog(title = "Contents", choices = chapters, selected = 1, onSelect = {}, onDismiss = {}) }

        composeRule.onNodeWithText("Chapter 60").performScrollTo().assertIsDisplayed()

        // Only the list scrolls: the dialog stays put, with its title showing
        composeRule.onNodeWithText("Contents").assertIsDisplayed()
    }

    @Test
    fun `a loading state can be found by its tag`() {
        show { LoadingState() }

        composeRule.onNodeWithTag(VaultTestTags.LOADING).assertExists()
    }

    @Test
    fun `sign out asks in place, and only yes signs out`() {
        var signedOut = 0
        show {
            SignOutSetting(
                label = "Sign out",
                confirmMessage = "Sign out of this server?",
                confirmDetail = "Your downloads stay on this device.",
                confirmLabel = "Yes, sign out",
                onSignOut = { signedOut++ },
            )
        }

        composeRule.onNodeWithText("Sign out").performClick()
        composeRule.onNodeWithText("Your downloads stay on this device.").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        signedOut shouldBe 0

        composeRule.onNodeWithText("Sign out").performClick()
        composeRule.onNodeWithText("Yes, sign out").performClick()
        signedOut shouldBe 1
    }

    @Test
    fun `the mini player shows what's loaded, and tapping it opens the player`() {
        var opened = 0
        show {
            MiniPlayerBar(
                title = "Dune",
                subtitle = "Frank Herbert",
                cover = {},
                isPlaying = false,
                onTogglePlayPause = {},
                onExpand = { opened++ },
            )
        }

        composeRule.onNodeWithText("Frank Herbert").assertExists()
        composeRule.onNodeWithText("Dune").performClick()

        opened shouldBe 1
    }

    @Test
    fun `the mini player's play button says what it does`() {
        var toggles = 0
        show {
            var playing by remember { mutableStateOf(false) }
            MiniPlayerBar(
                title = "Dune",
                subtitle = null,
                cover = {},
                isPlaying = playing,
                onTogglePlayPause = {
                    toggles++
                    playing = !playing
                },
                onExpand = {},
            )
        }

        composeRule.onNodeWithContentDescription("Play").performClick()
        composeRule.onNodeWithContentDescription("Pause").assertExists()

        toggles shouldBe 1
    }

    @Test
    fun `a list row can show how far through the item is`() {
        show { MediaListRow(title = "Dune", progress = 0.4f) }

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.4f, 0f..1f))).assertExists()
    }
}

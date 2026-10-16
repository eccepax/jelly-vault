package org.jellyfin.mobile.books.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.books.app.EpubFontFamily
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class BookSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val defaults = BookSettingsState(
        fontScale = 1.25f,
        fontFamily = EpubFontFamily.SANS_SERIF,
        lineSpacing = 1.5f,
        removeDownloadsOnSignOut = false,
    )
    private val events = mutableListOf<String>()

    private fun show(state: BookSettingsState = defaults) {
        composeRule.setContent {
            BookSettingsScreen(
                state = state,
                actions = BookSettingsActions(
                    onBack = { events += "back" },
                    onFontScale = { events += "fontScale $it" },
                    onFontFamily = { events += "fontFamily $it" },
                    onLineSpacing = { events += "lineSpacing $it" },
                    onRemoveDownloadsOnSignOut = { events += "removeOnSignOut $it" },
                    onSignOut = { events += "signOut" },
                ),
            )
        }
    }

    @Test
    fun `each reading setting shows its current value`() {
        show()

        composeRule.onNodeWithText("125%").assertIsDisplayed()
        composeRule.onNodeWithText("Sans-serif").assertIsDisplayed()
        composeRule.onNodeWithText("1.5x").assertIsDisplayed()
    }

    @Test
    fun `font size opens a picker with the current size selected`() {
        show()

        composeRule.onNodeWithText("Font size").performClick()
        composeRule.onNodeWithText("150%").assertIsDisplayed()
        composeRule.onNodeWithText("150%").performClick()

        events shouldBe listOf("fontScale 1.5")
        composeRule.onNodeWithText("150%").assertDoesNotExist()
    }

    @Test
    fun `font family and line spacing are picked the same way`() {
        show()

        composeRule.onNodeWithText("Font family").performClick()
        composeRule.onNodeWithText("Monospace").performClick()
        composeRule.onNodeWithText("Line spacing").performClick()
        composeRule.onNodeWithText("2.0x").performClick()

        events shouldBe listOf("fontFamily MONOSPACE", "lineSpacing 2.0")
    }

    @Test
    fun `the picker marks the value in use`() {
        show()

        composeRule.onNodeWithText("Line spacing").performClick()

        // The row's summary and the dialog's option share the label; the dialog's is the selectable one
        composeRule.onAllNodes(hasText("1.5x") and isSelectable())[0].assertIsSelected()
    }

    @Test
    fun `removing downloads on sign-out is off until switched on`() {
        show()

        composeRule.onNodeWithText("Remove downloads on sign out").performScrollTo().assertIsOff()
        composeRule.onNodeWithText("Remove downloads on sign out").performClick()

        events shouldBe listOf("removeOnSignOut true")
    }

    @Test
    fun `sign out asks first, and cancelling keeps you signed in`() {
        show()

        composeRule.onNodeWithText("Sign out").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performScrollTo().performClick()

        events shouldBe emptyList()
        composeRule.onNodeWithText("Sign out").assertIsDisplayed()
    }

    @Test
    fun `confirming signs out`() {
        show()

        composeRule.onNodeWithText("Sign out").performScrollTo().performClick()
        composeRule.onNodeWithText("Your downloaded books stay on this device.").assertIsDisplayed()
        composeRule.onNodeWithText("Yes, sign out").performScrollTo().performClick()

        events shouldBe listOf("signOut")
    }

    @Test
    fun `the sign-out prompt says when downloads will be removed`() {
        show(defaults.copy(removeDownloadsOnSignOut = true))

        composeRule.onNodeWithText("Sign out").performScrollTo().performClick()

        composeRule.onNodeWithText("Your downloaded books will be removed from this device.").assertIsDisplayed()
    }

    @Test
    fun `back leaves settings`() {
        show()

        composeRule.onNodeWithContentDescription("Back").performClick()

        events shouldBe listOf("back")
    }
}

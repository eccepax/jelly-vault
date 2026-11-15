package org.jellyfin.mobile.common.ui.components

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.common.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The bookmarks list both readers share: each opens, can have its note changed, and can be deleted. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BookmarksDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()
    private val chapter3 = BookmarkUi(id = 3L, label = "Chapter 3 · 12:34", note = "the twist", savedAt = 0L)

    private fun string(resId: Int, vararg args: Any) =
        ApplicationProvider.getApplicationContext<Application>().getString(resId, *args)

    private fun show(bookmarks: List<BookmarkUi>, editable: Boolean = true) {
        composeRule.setContent {
            BookmarksDialog(
                bookmarks = bookmarks,
                onOpen = { events += "open $it" },
                onDelete = { events += "delete $it" },
                onEditNote = if (editable) ({ id, note -> events += "note $id $note" }) else null,
                onDismiss = { events += "dismiss" },
            )
        }
    }

    @Test
    fun `each bookmark shows where it is and its note, and opens when tapped`() {
        show(listOf(chapter3))

        composeRule.onNodeWithText("the twist", substring = true).assertExists()
        composeRule.onNodeWithText("Chapter 3 · 12:34").performClick()

        events shouldBe listOf("dismiss", "open 3")
    }

    @Test
    fun `a bookmark can be deleted`() {
        show(listOf(chapter3))

        composeRule.onNodeWithContentDescription(string(R.string.delete_bookmark, chapter3.label)).performClick()

        events shouldBe listOf("delete 3")
    }

    @Test
    fun `a bookmark's note can be changed, starting from the note it has`() {
        show(listOf(chapter3))

        composeRule.onNodeWithContentDescription(string(R.string.edit_bookmark_note, chapter3.label)).performClick()
        composeRule.onNodeWithText("the twist").performTextReplacement("the big twist")
        composeRule.onNodeWithText(string(R.string.bookmark_save)).performClick()

        events shouldBe listOf("note 3 the big twist")
    }

    @Test
    fun `without a way to change notes there's no edit button`() {
        show(listOf(chapter3), editable = false)

        composeRule.onNodeWithContentDescription(
            string(R.string.edit_bookmark_note, chapter3.label),
        ).assertDoesNotExist()
    }

    @Test
    fun `with none, it says so`() {
        show(emptyList())

        composeRule.onNodeWithText(string(R.string.no_bookmarks_yet)).assertExists()
    }
}

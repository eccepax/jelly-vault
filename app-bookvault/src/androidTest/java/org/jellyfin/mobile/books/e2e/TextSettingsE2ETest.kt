package org.jellyfin.mobile.books.e2e

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.testing.VaultFixtures.Books
import org.junit.Assert.assertEquals
import org.junit.Test

/** A text setting chosen while reading is kept: it's there when the book is opened again, and for other books. */
@ReadingArea
class TextSettingsE2ETest : LoggedInE2ETest() {

    @Test
    fun aFontSizeChosenWhileReadingIsKept() {
        loginAndWaitForLibrary()
        openBook(Books.EPUB)

        readerMenu(R.string.text_settings_title)
        composeRule.onNodeWithText(context.getString(R.string.text_settings_font_size)).performClick()
        composeRule.onNodeWithText(LARGER).performClick()
        composeRule.waitForIdle()
        assertEquals(1.5f, koinGet<BookAppPreferences>().epubFontScale)

        leaveReader()
        openBook(Books.OTHER_EPUB)
        readerMenu(R.string.text_settings_title)
        waitForComposeText(LARGER)
    }

    private companion object {
        /** The font size chosen, as its picker and the settings row show it. */
        const val LARGER = "150%"
    }
}

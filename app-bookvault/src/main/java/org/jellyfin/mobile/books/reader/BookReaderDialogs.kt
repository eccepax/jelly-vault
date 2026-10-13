package org.jellyfin.mobile.books.reader

import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.data.entity.BookBookmarkEntity
import java.text.DateFormat
import java.util.Date

/**
 * Builds and shows the reader's non-trivial dialogs (text-settings pickers, table of contents /
 * page-jump) — extracted out of [BookReaderFragment] to keep it under this codebase's
 * `TooManyFunctions` threshold as the reader's feature surface has grown.
 */
internal class BookReaderDialogs(
    private val fragment: Fragment,
    private val viewModel: BookReaderViewModel,
    private val appPreferences: BookAppPreferences,
    private val onTextSettingsChanged: () -> Unit,
) {
    private val context get() = fragment.requireContext()
    private fun getString(resId: Int) = fragment.getString(resId)

    /** "Contents" toolbar action: a real chapter-title TOC for EPUB, a page-jump dialog for PDF. */
    fun showContents() {
        when (viewModel.bookType.value) {
            BookType.EPUB -> showTableOfContentsDialog()
            BookType.PDF -> showPageJumpDialog()
            else -> Unit
        }
    }

    private fun showTableOfContentsDialog() {
        val entries = viewModel.getTableOfContents()
        val titles = entries.map { it.title }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.contents_title)
            .setItems(titles) { _, which -> viewModel.setPage(entries[which].spineIndex) }
            .show()
    }

    private fun showPageJumpDialog() {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            id = R.id.dialog_page_input
            hint = getString(R.string.go_to_page_hint)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.go_to_page_title)
            .setView(input)
            .setPositiveButton(R.string.go) { _, _ ->
                val pageNumber = input.text.toString().toIntOrNull()
                if (pageNumber != null) viewModel.setPage(pageNumber - 1)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** "Aa" toolbar action: pick which of the 3 text settings to adjust, without leaving the book. */
    fun showTextSettingsChooser() {
        val options = arrayOf(
            getString(R.string.text_settings_font_size),
            getString(R.string.text_settings_font_family),
            getString(R.string.text_settings_line_spacing),
        )
        AlertDialog.Builder(context)
            .setTitle(R.string.text_settings_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showFontSizeDialog()
                    1 -> showFontFamilyDialog()
                    2 -> showLineSpacingDialog()
                }
            }
            .show()
    }

    fun showFontSizeDialog() {
        val labels = EPUB_FONT_SCALE_PRESETS.map { formatFontScaleLabel(it) }.toTypedArray()
        val currentIndex = EPUB_FONT_SCALE_PRESETS.indexOf(appPreferences.epubFontScale).coerceAtLeast(0)
        AlertDialog.Builder(context)
            .setTitle(R.string.text_settings_font_size)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                appPreferences.epubFontScale = EPUB_FONT_SCALE_PRESETS[which]
                onTextSettingsChanged()
                dialog.dismiss()
            }
            .show()
    }

    private fun showFontFamilyDialog() {
        val labels = EPUB_FONT_FAMILY_PRESETS.map { formatFontFamilyLabel(it) }.toTypedArray()
        val currentIndex = EPUB_FONT_FAMILY_PRESETS.indexOf(appPreferences.epubFontFamily).coerceAtLeast(0)
        AlertDialog.Builder(context)
            .setTitle(R.string.text_settings_font_family)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                appPreferences.epubFontFamily = EPUB_FONT_FAMILY_PRESETS[which]
                onTextSettingsChanged()
                dialog.dismiss()
            }
            .show()
    }

    private fun showLineSpacingDialog() {
        val labels = EPUB_LINE_SPACING_PRESETS.map { formatLineSpacingLabel(it) }.toTypedArray()
        val currentIndex = EPUB_LINE_SPACING_PRESETS.indexOf(appPreferences.epubLineSpacing).coerceAtLeast(0)
        AlertDialog.Builder(context)
            .setTitle(R.string.text_settings_line_spacing)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                appPreferences.epubLineSpacing = EPUB_LINE_SPACING_PRESETS[which]
                onTextSettingsChanged()
                dialog.dismiss()
            }
            .show()
    }

    /** "Add bookmark" toolbar action: prompts for an optional note, then bookmarks the current position. */
    fun showAddBookmarkDialog() {
        val input = EditText(context).apply {
            id = R.id.dialog_bookmark_note_input
            hint = getString(R.string.add_bookmark_note_hint)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.add_bookmark_title)
            .setView(input)
            .setPositiveButton(R.string.add_bookmark_action) { _, _ -> viewModel.addBookmark(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** "Bookmarks" toolbar action: lists saved bookmarks; tapping one offers to jump to it or delete it. */
    fun showBookmarksDialog() {
        val bookmarks = viewModel.bookmarks.value
        if (bookmarks.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle(R.string.bookmarks_title)
                .setMessage(R.string.no_bookmarks_yet)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val labels = bookmarks.map { bookmarkListLabel(it) }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.bookmarks_title)
            .setItems(labels) { _, which -> showBookmarkActionDialog(bookmarks[which]) }
            .show()
    }

    private fun bookmarkListLabel(bookmark: BookBookmarkEntity): String {
        val timestamp = DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
        ).format(Date(bookmark.createdAt))
        val note = bookmark.note?.let { " — $it" }.orEmpty()
        return "${bookmark.label} ($timestamp)$note"
    }

    private fun showBookmarkActionDialog(bookmark: BookBookmarkEntity) {
        val actions = arrayOf(bookmark.label, getString(R.string.delete))
        AlertDialog.Builder(context)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> viewModel.jumpToBookmark(bookmark)
                    1 -> viewModel.deleteBookmark(bookmark.id)
                }
            }
            .show()
    }
}

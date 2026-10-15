package org.jellyfin.mobile.books.library

import android.app.Application
import android.content.DialogInterface
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.app.LibraryViewMode
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.util.UUID
import org.jellyfin.mobile.common.R as CommonR

/**
 * Tests the book library's long-press download menu.
 *
 * [BookLibraryFragment] shows an `androidx.appcompat.app.AlertDialog`, not the framework
 * `android.app.AlertDialog` — [ShadowDialog.getLatestDialog] (which tracks any shown [android.app.Dialog]
 * subclass) is used instead of the framework-only `ShadowAlertDialog`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BookLibraryFragmentTest {

    private val libraryViewModel = mockk<BookLibraryViewModel>(relaxed = true)
    private val bookAppPreferences = mockk<BookAppPreferences>(relaxed = true)
    private val downloadedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val tagFilter = MutableStateFlow<String?>(null)
    private var scenario: FragmentScenario<BookLibraryFragment>? = null

    @Before
    fun setUp() {
        every { libraryViewModel.filteredBooks } returns MutableStateFlow(emptyList())
        every { libraryViewModel.loading } returns MutableStateFlow(false)
        every { libraryViewModel.error } returns MutableStateFlow(null)
        every { libraryViewModel.downloadedIds } returns downloadedIdsFlow
        every { libraryViewModel.showDownloadedOnly } returns MutableStateFlow(false)
        every { libraryViewModel.tagFilter } returns tagFilter
        every { bookAppPreferences.getLibraryViewMode(any()) } returns LibraryViewMode.LIST

        startKoin {
            modules(
                module {
                    single { bookAppPreferences }
                    viewModel { libraryViewModel }
                },
            )
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
        stopKoin()
        ShadowDialog.reset()
    }

    private fun book(name: String = "My Book") =
        BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.BOOK, name = name)

    private fun string(resId: Int): String =
        ApplicationProvider.getApplicationContext<Application>().getString(resId)

    @Test
    fun `the book list ends above the system navigation bar`() {
        val navBarPx = 126
        launchFragmentInContainer<BookLibraryFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment ->
                val bars = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navBarPx))
                    .build()
                ViewCompat.dispatchApplyWindowInsets(fragment.requireView(), bars)
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onFragment { fragment ->
                val root = fragment.requireView()
                root.findViewById<View>(R.id.recycler_view_list).bottom shouldBeLessThanOrEqual root.height - navBarPx
            }
        }
    }

    @Test
    fun `long-press on a not-yet-downloaded book starts the download directly, no dialog`() {
        val target = book()

        launchFragmentInContainer<BookLibraryFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment -> fragment.showDownloadDialog(target) }
        }

        verify { libraryViewModel.downloadBook(target) }
        ShadowDialog.getLatestDialog() shouldBe null
        ShadowToast.getTextOfLatestToast() shouldBe string(R.string.downloading_book)
    }

    @Test
    fun `long-press on an already-downloaded book offers to remove it`() {
        val target = book()
        downloadedIdsFlow.value = setOf(target.id.toString())

        launchFragmentInContainer<BookLibraryFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment -> fragment.showDownloadDialog(target) }

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog

            // Tap the positive ("OK") button to confirm removal.
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }

        verify { libraryViewModel.deleteDownload(target) }
    }

    // --- Filter by tag ----------------------------------------------------------------------------

    @Test
    fun `filtering by tag offers every tag in the library, after all tags, with the current one checked`() {
        every { libraryViewModel.getAvailableTags() } returns listOf("Fantasy", "Sci-Fi")
        tagFilter.value = "Sci-Fi"

        val dialog = openTagFilter()

        dialog.items() shouldBe listOf(string(R.string.filter_all_tags), "Fantasy", "Sci-Fi")
        dialog.listView.checkedItemPosition shouldBe 2
    }

    @Test
    fun `choosing a tag narrows the library to it`() {
        every { libraryViewModel.getAvailableTags() } returns listOf("Fantasy", "Sci-Fi")

        openTagFilter().choose(1)

        tagFilter.value shouldBe "Fantasy"
    }

    @Test
    fun `choosing all tags shows every book again`() {
        every { libraryViewModel.getAvailableTags() } returns listOf("Fantasy", "Sci-Fi")
        tagFilter.value = "Fantasy"

        openTagFilter().choose(0)

        tagFilter.value shouldBe null
    }

    @Test
    fun `with no tags in the library, filtering by tag says so`() {
        every { libraryViewModel.getAvailableTags() } returns emptyList()

        val dialog = openTagFilter()

        dialog.listView shouldBe null
        dialog.findViewById<TextView>(android.R.id.message)?.text?.toString() shouldBe string(R.string.filter_no_tags)
    }

    private fun openTagFilter(): AlertDialog {
        scenario = launchFragmentInContainer<BookLibraryFragment>(themeResId = CommonR.style.AppTheme)
        scenario?.onFragment { fragment ->
            fragment.requireView().findViewById<Toolbar>(
                R.id.toolbar,
            ).menu.performIdentifierAction(R.id.action_filter_tag, 0)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    private fun AlertDialog.items() = (0 until listView.adapter.count).map { listView.adapter.getItem(it).toString() }

    private fun AlertDialog.choose(position: Int) {
        shadowOf(listView).performItemClick(position)
        shadowOf(Looper.getMainLooper()).idle()
    }
}

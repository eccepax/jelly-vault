package org.jellyfin.mobile.books.library

import android.app.Application
import android.content.DialogInterface
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    @Before
    fun setUp() {
        every { libraryViewModel.filteredBooks } returns MutableStateFlow(emptyList())
        every { libraryViewModel.loading } returns MutableStateFlow(false)
        every { libraryViewModel.error } returns MutableStateFlow(null)
        every { libraryViewModel.downloadedIds } returns downloadedIdsFlow
        every { libraryViewModel.showDownloadedOnly } returns MutableStateFlow(false)
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
}

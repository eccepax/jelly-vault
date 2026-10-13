package org.jellyfin.mobile.books.settings

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.testing.launchFragmentInContainer
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.mockk.mockk
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.common.MainViewModel
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
import org.jellyfin.mobile.common.R as CommonR

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BookSettingsFragmentTest {

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single { mockk<BookAppPreferences>(relaxed = true) }
                    viewModel { mockk<MainViewModel>(relaxed = true) }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `the settings screen stays clear of the status and navigation bars`() {
        launchFragmentInContainer<BookSettingsFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment ->
                val bars = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, STATUS_BAR_PX, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAV_BAR_PX))
                    .build()
                ViewCompat.dispatchApplyWindowInsets(fragment.requireView(), bars)
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onFragment { fragment ->
                val root = fragment.requireView()
                root.findViewById<View>(R.id.toolbar).paddingTop shouldBeGreaterThanOrEqual STATUS_BAR_PX
                root.findViewById<View>(R.id.recycler_view).bottom shouldBeLessThanOrEqual root.height - NAV_BAR_PX
            }
        }
    }

    private companion object {
        const val STATUS_BAR_PX = 63
        const val NAV_BAR_PX = 126
    }
}

package org.jellyfin.mobile.books

import android.app.Application
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.library.BookLibraryFragment
import org.jellyfin.mobile.books.library.BookLibraryViewModel
import org.jellyfin.mobile.books.setup.BookConnectFragment
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.ServerState
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.jellyfin.mobile.common.setup.LoginFragment
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rotating the device on a non-Library screen (reader, settings, ...) must not send the user back
 * to the Library. [BookMainActivity] re-subscribes to [MainViewModel.serverState] on every
 * recreation and gets the cached `ServerState.Available` replayed, so it must only navigate to the
 * Library when first entering the signed-in area, not on a replay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BookMainActivityTest {

    private val mainViewModel = mockk<MainViewModel>(relaxed = true)
    private val appPreferences = mockk<BookAppPreferences>(relaxed = true)

    // BookLibraryFragment resolves its own ViewModel once it's actually started (not just placed
    // in the container) — needed so the fragment reached by the navigation fix under test doesn't
    // itself crash with a NoDefinitionFoundException.
    private val libraryViewModel = mockk<BookLibraryViewModel>(relaxed = true)
    private val serverStateFlow = MutableStateFlow<ServerState>(
        ServerState.Available(ServerEntity(hostname = "jellyfin.example.com")),
    )

    @Before
    fun setUp() {
        every { mainViewModel.serverState } returns serverStateFlow
        every { appPreferences.currentUserId } returns 1L
        every { libraryViewModel.filteredBooks } returns MutableStateFlow(emptyList())
        every { libraryViewModel.loading } returns MutableStateFlow(false)
        every { libraryViewModel.error } returns MutableStateFlow(null)
        every { libraryViewModel.downloadedIds } returns MutableStateFlow(emptySet())
        every { libraryViewModel.showDownloadedOnly } returns MutableStateFlow(false)

        startKoin {
            modules(
                module {
                    viewModel { mainViewModel }
                    viewModel { libraryViewModel }
                    single { appPreferences }
                    // Needed once BookConnectFragment's ComposeView actually renders (ServerSelection
                    // resolves it directly via Koin) — not otherwise exercised by this test.
                    single { mockk<ApiClientController>(relaxed = true) }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `fresh launch with an available server and a signed-in user shows the Library`() {
        ActivityScenario.launch(BookMainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
                    .shouldBeInstanceOf<BookLibraryFragment>()
            }
        }
    }

    @Test
    fun `a fragment already shown after login is replaced with the Library once signed in`() {
        every { appPreferences.currentUserId } returns null

        ActivityScenario.launch(BookMainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
                    .shouldBeInstanceOf<LoginFragment>()
            }

            // Simulates a completed login: currentUserId becomes set and the ViewModel emits a
            // fresh (distinct) Available instance, the same way LoginFragment's real
            // `mainViewModel.refreshState()` call does.
            every { appPreferences.currentUserId } returns 1L
            serverStateFlow.value = ServerState.Available(ServerEntity(hostname = "jellyfin.example.com"))

            scenario.onActivity { activity ->
                activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
                    .shouldBeInstanceOf<BookLibraryFragment>()
            }
        }
    }

    @Test
    fun `rotating while a non-Library screen is showing does not kick back to the Library`() {
        ActivityScenario.launch(BookMainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // A minimal stand-in for any already-signed-in screen (reader, settings, ...) —
                // the Activity-level navigation policy under test cares only about fragment
                // *type*, not behavior, so a marker fragment keeps this test isolated from
                // BookReaderFragment's own (separately tested) concerns.
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, MarkerFragment())
                    .commit()
                activity.supportFragmentManager.executePendingTransactions()
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
                    .shouldBeInstanceOf<MarkerFragment>()
            }
        }
    }

    // --- shouldNavigateToLibrary (pure function; no Activity/FragmentManager needed) -----------------
    //
    // BookConnectFragment's real render pulls in a deep Compose dependency chain (ApiClientController,
    // ConnectionHelper, ...) unrelated to this decision, so its "should navigate away" case is tested
    // directly against the extracted pure function instead of through a rendered Activity screen —
    // the three tests above already cover the parts of this logic that genuinely need a real
    // Activity + FragmentManager (initial launch, the post-login transition, and the rotation fix).

    @Test
    fun `shouldNavigateToLibrary is true with no fragment restored yet`() {
        shouldNavigateToLibrary(null) shouldBe true
    }

    @Test
    fun `shouldNavigateToLibrary is true while still on the login screen`() {
        shouldNavigateToLibrary(LoginFragment()) shouldBe true
    }

    @Test
    fun `shouldNavigateToLibrary is true while still on the connect screen`() {
        shouldNavigateToLibrary(BookConnectFragment()) shouldBe true
    }

    @Test
    fun `shouldNavigateToLibrary is false for any other already-signed-in screen`() {
        shouldNavigateToLibrary(MarkerFragment()) shouldBe false
    }
}

class MarkerFragment : Fragment()

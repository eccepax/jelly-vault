package org.jellyfin.mobile

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ActivityScenario
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.downloads.DownloadMethod
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.library.LibraryFragment
import org.jellyfin.mobile.library.LibraryItem
import org.jellyfin.mobile.library.LibraryViewModel
import org.jellyfin.mobile.library.SortOption
import org.jellyfin.mobile.player.ui.PlayerFragment
import org.jellyfin.mobile.robolectric.playerViewModel
import org.jellyfin.mobile.settings.SettingsActions
import org.jellyfin.mobile.settings.SettingsScreen
import org.jellyfin.mobile.settings.SettingsUiState
import org.jellyfin.mobile.testsupport.PHONE_QUALIFIERS
import org.jellyfin.mobile.testsupport.SCREENSHOT_SDK
import org.jellyfin.mobile.testsupport.captureWithSystemBars
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID
import org.jellyfin.mobile.common.R as CommonR

/** AudioVault's main screens on a phone with 3-button navigation; see [captureWithSystemBars]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [SCREENSHOT_SDK], qualifiers = PHONE_QUALIFIERS)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AudioVaultScreenshotTest {

    private val apiClient = mockk<ApiClient>(relaxed = true).apply {
        every { baseUrl } returns "https://server"
        every { accessToken } returns "token"
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkStatic(API_CLIENT_EXTENSIONS)
    }

    @Test
    fun library() {
        val audiobooks = listOf(
            "A People's History of the United States", "The Body Keeps the Score", "Born a Crime",
            "Braiding Sweetgrass", "Caste", "Educated", "Harry Potter and the Philosopher's Stone",
            "Just Mercy", "The Palestine Laboratory", "Project Hail Mary", "Sapiens", "The Warmth of Other Suns",
        ).map {
            LibraryItem.AudioFile(
                BaseItemDto(id = UUID.nameUUIDFromBytes(it.toByteArray()), type = BaseItemKind.AUDIO_BOOK, name = it),
            )
        }
        val library = mockk<LibraryViewModel>(relaxed = true).apply {
            every { items } returns MutableStateFlow(audiobooks)
            every { downloadedIds } returns MutableStateFlow(emptySet())
            every { hiddenIds } returns MutableStateFlow(emptySet())
            every { title } returns MutableStateFlow("All Audiobooks")
            every { canGoUp } returns MutableStateFlow(false)
            every { isLoading } returns MutableStateFlow(false)
            every { error } returns MutableStateFlow(null)
            every { sortOption } returns MutableStateFlow(SortOption.TITLE)
            every { showDownloadedOnly } returns MutableStateFlow(false)
            every { showHiddenItems } returns MutableStateFlow(false)
            every { filterQuery } returns MutableStateFlow("")
        }
        // A relaxed ApiClient's cached-API lookup returns the wrong type; covers come from a real ImageApi
        mockkStatic(API_CLIENT_EXTENSIONS)
        every { apiClient.imageApi } returns ImageApi(apiClient)
        startKoin {
            modules(
                module {
                    single { mockk<ActivityEventHandler>(relaxed = true) }
                    single { apiClient }
                    viewModel { library }
                    viewModel { playerViewModel() }
                },
            )
        }

        launchFragmentInContainer<LibraryFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { it.requireView().captureWithSystemBars("library") }
        }
    }

    @Test
    fun player() {
        startKoin {
            modules(
                module {
                    single { apiClient }
                    single { mockk<AppPreferences>(relaxed = true) }
                    single { mockk<AudioVaultAppPreferences>(relaxed = true) }
                    viewModel { playerViewModel() }
                },
            )
        }

        launchFragmentInContainer<PlayerFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { it.requireView().captureWithSystemBars("player") }
        }
    }

    /** The screen itself, not its Fragment: it reads nothing but the [SettingsUiState] it's given. */
    @Test
    fun settings() {
        val state = SettingsUiState(
            holdToSpeedUp = true,
            skipSilence = false,
            flatLibrary = false,
            downloadMethod = DownloadMethod.WIFI_ONLY,
            removeDownloadsOnSignOut = true,
            appName = "AudioVault",
            appVersion = "1.0.0",
        )

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent { AppTheme(VaultBrand.AudioVault) { SettingsScreen(state, SettingsActions()) } }
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { it.findViewById<View>(android.R.id.content).captureWithSystemBars("settings") }
        }
    }

    private companion object {
        const val API_CLIENT_EXTENSIONS = "org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt"
    }
}

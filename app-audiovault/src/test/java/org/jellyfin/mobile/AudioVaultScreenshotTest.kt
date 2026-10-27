package org.jellyfin.mobile

import android.app.Application
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.downloads.DownloadRequests
import org.jellyfin.mobile.library.LibraryItem
import org.jellyfin.mobile.library.LibraryRoute
import org.jellyfin.mobile.library.LibraryViewModel
import org.jellyfin.mobile.library.SortOption
import org.jellyfin.mobile.library.jellyfinCoverUrl
import org.jellyfin.mobile.player.playback.FakeAudiobookController
import org.jellyfin.mobile.player.ui.PlayerRoute
import org.jellyfin.mobile.robolectric.playerViewModel
import org.jellyfin.mobile.settings.SettingsRoute
import org.jellyfin.mobile.settings.SettingsViewModel
import org.jellyfin.mobile.testing.downloads.FakeDownloadSettings
import org.jellyfin.mobile.testsupport.PHONE_QUALIFIERS
import org.jellyfin.mobile.testsupport.SCREENSHOT_SDK
import org.jellyfin.mobile.testsupport.captureWithSystemBars
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.operations.ImageApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID

/** AudioVault's main screens on a phone with 3-button navigation; see [captureWithSystemBars]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [SCREENSHOT_SDK], qualifiers = PHONE_QUALIFIERS)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AudioVaultScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val apiClient = mockk<ApiClient>(relaxed = true).apply {
        every { baseUrl } returns "https://server"
        every { accessToken } returns "token"
    }

    @After
    fun tearDown() {
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
            every { waitingIds } returns MutableStateFlow(emptySet())
            every { hiddenIds } returns MutableStateFlow(emptySet())
            every { title } returns MutableStateFlow("All Audiobooks")
            every { canGoUp } returns MutableStateFlow(false)
            every { isLoading } returns MutableStateFlow(false)
            every { error } returns MutableStateFlow(null)
            every { sortOption } returns MutableStateFlow(SortOption.TITLE)
            every { showDownloadedOnly } returns MutableStateFlow(false)
            every { showHiddenItems } returns MutableStateFlow(false)
            every { filterQuery } returns MutableStateFlow("")
            every { folderId } returns MutableStateFlow(null)
        }
        // A relaxed ApiClient's cached-API lookup returns the wrong type; covers come from a real ImageApi
        mockkStatic(API_CLIENT_EXTENSIONS)
        every { apiClient.imageApi } returns ImageApi(apiClient)

        capture("library") {
            LibraryRoute(
                onPlay = {},
                onOpenPlayer = {},
                onOpenSettings = {},
                viewModel = library,
                playerViewModel = playerViewModel(),
                downloads = NO_DOWNLOADS,
                coverUrl = jellyfinCoverUrl(apiClient),
            )
        }
    }

    @Test
    fun player() {
        capture("player") { PlayerRoute(onBack = {}, viewModel = playerViewModel()) }
    }

    @Test
    fun settings() {
        val preferences = mockk<AudioVaultAppPreferences>(relaxed = true).apply {
            every { exoPlayerAllowPressSpeedUp } returns true
            every { skipSilenceEnabled } returns false
            every { libraryGlobalFlattened } returns false
            every { removeDownloadsOnSignOut } returns true
        }
        val settings = SettingsViewModel(
            preferences = preferences,
            downloads = FakeDownloadSettings().apply { bytesUsed.value = 1_250_000_000L },
            playback = FakeAudiobookController(),
            signOut = {},
            app = ClientInfo(name = "AudioVault", version = "1.0.0"),
        )

        capture("settings") { SettingsRoute(onBack = {}, onSignedOut = {}, viewModel = settings) }
    }

    private fun capture(name: String, screen: @Composable () -> Unit) {
        composeRule.setContent { AppTheme(VaultBrand.AudioVault, content = screen) }
        composeRule.waitForIdle()
        composeRule.activity.findViewById<View>(android.R.id.content).captureWithSystemBars(name)
    }

    private companion object {
        val NO_DOWNLOADS = object : DownloadRequests {
            override fun download(trackIds: List<String>) = Unit
            override fun remove(trackIds: List<String>) = Unit
        }
        const val API_CLIENT_EXTENSIONS = "org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt"
    }
}

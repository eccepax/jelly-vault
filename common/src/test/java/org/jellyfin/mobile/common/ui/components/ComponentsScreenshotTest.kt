package org.jellyfin.mobile.common.ui.components

import android.app.Application
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.common.ui.theme.VaultSpacing
import org.jellyfin.mobile.testsupport.PHONE_QUALIFIERS
import org.jellyfin.mobile.testsupport.SCREENSHOT_SDK
import org.jellyfin.mobile.testsupport.captureWithSystemBars
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The shared components as each app will show them; see [captureWithSystemBars]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [SCREENSHOT_SDK], qualifiers = PHONE_QUALIFIERS)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentsScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, brand: VaultBrand, content: @Composable () -> Unit) {
        composeRule.setContent {
            AppTheme(brand) {
                // As a screen does: clear of the status bar (VaultBottomBar clears the navigation bar)
                Surface(
                    color = MaterialTheme.colors.background,
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    content = content,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.activity.findViewById<View>(android.R.id.content).captureWithSystemBars(name)
    }

    @Test
    fun lists() = capture("components-lists", VaultBrand.TuneVault) {
        Column {
            VaultTopBar(
                title = "Library",
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Filled.Settings, contentDescription = null) }
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                SectionTitle("Recently played")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VaultSpacing.Medium),
                    modifier = Modifier.padding(horizontal = VaultSpacing.Large),
                ) {
                    MediaGridCard(
                        title = "The Purple Bird",
                        subtitle = "Bonnie \"Prince\" Billy",
                        cover = { CoverArt(url = null, placeholder = Icons.Filled.Album) },
                        onClick = {},
                        modifier = Modifier.width(140.dp),
                    )
                    MediaGridCard(
                        title = "Dune",
                        subtitle = "Frank Herbert",
                        cover = { CoverArt(url = null, placeholder = Icons.Filled.MenuBook, aspectRatio = BOOK) },
                        onClick = {},
                        modifier = Modifier.width(140.dp),
                    )
                }
                SectionTitle("Songs")
                MediaListRow(
                    title = "London May",
                    subtitle = "Bonnie \"Prince\" Billy · The Purple Bird",
                    cover = { CoverArt(url = null, placeholder = Icons.Filled.Album) },
                    highlighted = true,
                    onClick = {},
                ) {
                    DownloadedMark()
                    SecondaryText("3:00")
                }
                MediaListRow(
                    title = "A People's History of the United States, a very long title that wraps",
                    subtitle = "Howard Zinn",
                    cover = { CoverArt(url = null, placeholder = Icons.Filled.Headphones) },
                    onClick = {},
                )
                MediaListRow(title = "A row with no cover or subtitle", onClick = {})
            }
            OfflineBanner()
            VaultBottomBar {
                VaultBottomBarItem(selected = true, onClick = {}, icon = Icons.Filled.Home, label = "Home")
                VaultBottomBarItem(selected = false, onClick = {}, icon = Icons.Filled.LibraryMusic, label = "Library")
                VaultBottomBarItem(selected = false, onClick = {}, icon = Icons.Filled.Search, label = "Search")
            }
        }
    }

    @Test
    fun settings() = capture("components-settings", VaultBrand.BookVault) {
        Column {
            VaultTopBar(
                title = "Settings",
                navigationIcon = {
                    IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
            )
            SettingsSectionHeader("Reading")
            SettingsActionRow(label = "Font size", summary = "Medium", onClick = {})
            SettingsSwitchRow(label = "Keep the screen on", checked = true, onCheckedChange = {})
            SettingsSwitchRow(
                label = "Download on Wi-Fi only",
                summary = "Downloads wait until you're on Wi-Fi",
                checked = false,
                onCheckedChange = {},
            )
            SettingsSectionHeader("Streaming quality")
            SettingsNote("Lower quality uses less data.")
            SettingsRadioGroup(
                choices = listOf(Choice(0, "Original", detail = "As stored on the Vault"), Choice(1, "Data saver")),
                selected = 0,
                onSelect = {},
            )
            SettingsSectionHeader("Account")
            InlineConfirm(
                message = "Sign out of the Vault?",
                detail = "Your downloads will be removed.",
                confirmLabel = "Yes, sign out",
                onConfirm = {},
                onCancel = {},
            )
        }
    }

    @Test
    fun states() = capture("components-states", VaultBrand.AudioVault) {
        Column {
            SearchField(query = "dune", onQueryChange = {}, placeholder = "Search")
            Column(modifier = Modifier.weight(1f)) { LoadingState() }
            Column(modifier = Modifier.weight(1f)) {
                EmptyState(message = "Nothing downloaded yet", icon = Icons.Filled.Headphones)
            }
            Column(modifier = Modifier.weight(1f)) {
                ErrorState(message = "Couldn't reach the Vault", onRetry = {})
            }
        }
    }

    private companion object {
        const val BOOK = 2f / 3f
    }
}

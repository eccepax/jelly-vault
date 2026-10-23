package org.jellyfin.mobile.books

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.navigation.BookVaultRoot
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.common.ui.utils.enableDarkEdgeToEdge
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

// MainViewModel is resolved here (not via koinViewModel() in Compose) so it's provably the one
// activity-scoped instance that the sign-in screen tells once the user is saved.
class BookMainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModel()
    private val appPreferences: BookAppPreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableDarkEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme(VaultBrand.BookVault) {
                BookVaultRoot(mainViewModel, appPreferences)
            }
        }
    }
}

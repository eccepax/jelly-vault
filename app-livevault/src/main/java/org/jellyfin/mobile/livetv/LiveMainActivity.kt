package org.jellyfin.mobile.livetv

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.ui.VaultRoot
import org.jellyfin.mobile.common.ui.utils.AppTheme
import org.jellyfin.mobile.livetv.ui.LiveTvHome
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

// AppCompatActivity (a FragmentActivity): common's LoginFragment is hosted inside the Compose tree.
// MainViewModel is resolved here so it's the same activity-scoped instance LoginFragment gets.
class LiveMainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModel()
    private val appPreferences: AppPreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                VaultRoot(mainViewModel, appPreferences, logoRes = R.drawable.live_logo) { LiveTvHome() }
            }
        }
    }
}

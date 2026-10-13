package org.jellyfin.mobile.music

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.ui.utils.AppTheme
import org.jellyfin.mobile.music.ui.TuneVaultRoot
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

// AppCompatActivity (a FragmentActivity), not a bare ComponentActivity: common's LoginFragment is
// hosted inside the Compose tree and needs a FragmentManager. MainViewModel is resolved here (not
// via koinViewModel() in Compose) so it's provably the same activity-scoped instance LoginFragment
// gets from activityViewModel().
class TuneMainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModel()
    private val appPreferences: AppPreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                TuneVaultRoot(mainViewModel, appPreferences)
            }
        }
    }
}

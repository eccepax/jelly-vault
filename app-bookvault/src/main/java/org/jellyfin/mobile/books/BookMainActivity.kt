package org.jellyfin.mobile.books

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.library.BookLibraryFragment
import org.jellyfin.mobile.books.setup.BookConnectFragment
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.ServerState
import org.jellyfin.mobile.common.setup.LoginFragment
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class BookMainActivity : AppCompatActivity() {

    val mainViewModel: MainViewModel by viewModel()
    private val appPreferences: BookAppPreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            mainViewModel.serverState.collectLatest { state ->
                lifecycle.withStarted {
                    handleServerState(state)
                }
            }
        }
    }

    private fun handleServerState(state: ServerState) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        when (state) {
            ServerState.Pending -> {
                // no-op: show splash
            }
            is ServerState.Unset -> showConnectFragment(currentFragment)
            is ServerState.Available -> showAvailableFragment(currentFragment)
        }
    }

    private fun showConnectFragment(currentFragment: Fragment?) {
        if (currentFragment !is BookConnectFragment) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, BookConnectFragment())
                .commit()
        }
    }

    private fun showAvailableFragment(currentFragment: Fragment?) {
        when (appPreferences.currentUserId) {
            null -> if (currentFragment !is LoginFragment) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, LoginFragment())
                    .commit()
            }
            else -> if (shouldNavigateToLibrary(currentFragment)) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, BookLibraryFragment.newInstance())
                    .commit()
            }
        }
    }
}

/**
 * True when the signed-in area should be entered fresh — no fragment restored yet, or still on a
 * pre-Library fragment (login/connect) that hasn't navigated away on its own. False for any
 * fragment already inside the signed-in app (Library itself, reader, settings, ...), including one
 * [FragmentManager] has just correctly restored after a config change — that must NOT be replaced
 * just because [MainViewModel.serverState] replays its cached value to the fresh collector a
 * recreated Activity gets on every rotation.
 */
internal fun shouldNavigateToLibrary(currentFragment: Fragment?): Boolean =
    currentFragment == null || currentFragment is LoginFragment || currentFragment is BookConnectFragment

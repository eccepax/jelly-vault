package org.jellyfin.mobile

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.ServerState
import org.jellyfin.mobile.common.setup.LoginFragment
import org.jellyfin.mobile.common.ui.utils.enableDarkEdgeToEdge
import org.jellyfin.mobile.downloads.AudiobookDownloads
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.library.LibraryFragment
import org.jellyfin.mobile.setup.ConnectFragment
import org.jellyfin.mobile.utils.BackPressInterceptor
import org.jellyfin.mobile.utils.PermissionRequestHelper
import org.jellyfin.mobile.utils.extensions.replaceFragment
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.fragment.android.setupKoinFragmentFactory
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    private val activityEventHandler: ActivityEventHandler = get()
    val mainViewModel: MainViewModel by viewModel()
    private val appPreferences: AudioVaultAppPreferences by inject()
    private val permissionRequestHelper: PermissionRequestHelper by inject()

    /**
     * Passes back press events onto the currently visible [Fragment] if it implements the [BackPressInterceptor] interface.
     *
     * If the current fragment does not implement [BackPressInterceptor] or has decided not to intercept the event
     * (see result of [BackPressInterceptor.onInterceptBackPressed]), the topmost backstack entry will be popped.
     *
     * If there is no topmost backstack entry, the event will be passed onto the dispatcher's fallback handler.
     */
    private val onBackPressedCallback: OnBackPressedCallback.() -> Unit = callback@{
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is BackPressInterceptor && currentFragment.onInterceptBackPressed()) {
            // Top fragment handled back press
            return@callback
        }

        // This is the same default action as in Activity.onBackPressed
        if (!supportFragmentManager.isStateSaved && supportFragmentManager.popBackStackImmediate()) {
            // Removed fragment from back stack
            return@callback
        }

        // Let the system handle the back press
        isEnabled = false
        // assert() is a no-op on Android in release builds — use a real log instead.
        if (onBackPressedDispatcher.hasEnabledCallbacks()) {
            Timber.w("Unexpected back-press callbacks still registered; MainActivity should be the lowest handler")
        }
        onBackPressedDispatcher.onBackPressed()
        isEnabled = true // re-enable callback in case activity isn't finished
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        setupKoinFragmentFactory()
        enableDarkEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Subscribe to activity events
        with(activityEventHandler) { subscribe() }

        // Load UI
        lifecycleScope.launch {
            mainViewModel.serverState.collectLatest { state ->
                lifecycle.withStarted {
                    handleServerState(state)
                }
            }
        }

        // Once there's a server, fetch again any downloaded track whose file has gone missing
        lifecycleScope.launch {
            mainViewModel.serverState.first { it is ServerState.Available }
            get<AudiobookDownloads>().repair()
        }

        // Handle back presses
        onBackPressedDispatcher.addCallback(this, onBackPressed = onBackPressedCallback)
    }

    private fun handleServerState(state: ServerState) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        when (state) {
            ServerState.Pending -> {
                // TODO add loading indicator
            }
            is ServerState.Unset -> {
                if (currentFragment !is ConnectFragment) supportFragmentManager.replaceFragment<ConnectFragment>()
            }
            is ServerState.Available -> handleAvailableServerState(currentFragment)
        }
    }

    /** Requires selecting/logging in as a user before entering the library. */
    private fun handleAvailableServerState(currentFragment: Fragment?) {
        when (appPreferences.currentUserId) {
            null -> if (currentFragment !is LoginFragment) supportFragmentManager.replaceFragment<LoginFragment>()
            else -> if (shouldNavigateToLibrary(currentFragment)) {
                supportFragmentManager.replaceFragment<LibraryFragment>()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        permissionRequestHelper.handleRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

/**
 * True when the signed-in area should be entered fresh — no fragment restored yet, or still on a
 * pre-Library fragment (login/connect) that hasn't navigated away on its own. False for any
 * fragment already inside the signed-in app (Library itself, the player, settings, ...), including
 * one [FragmentManager] has just correctly restored after a config change — that must NOT be
 * replaced just because [MainViewModel.serverState] replays its cached value to the fresh
 * collector a recreated Activity gets on every rotation.
 */
internal fun shouldNavigateToLibrary(currentFragment: Fragment?): Boolean =
    currentFragment == null || currentFragment is LoginFragment || currentFragment is ConnectFragment

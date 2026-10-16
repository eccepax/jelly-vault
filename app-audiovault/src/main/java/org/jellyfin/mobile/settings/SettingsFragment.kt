package org.jellyfin.mobile.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.offline.DownloadManager
import kotlinx.coroutines.launch
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.downloads.applyDownloadMethod
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class SettingsFragment : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModel()
    private val appPreferences: AudioVaultAppPreferences by inject()
    private val downloadManager: DownloadManager by inject()
    private val clientInfo: ClientInfo by inject()

    private var _binding: FragmentComposeBinding? = null
    private val binding get() = _binding!!

    private var state by mutableStateOf<SettingsUiState?>(null)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentComposeBinding.inflate(inflater, container, false)
        return binding.composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.requestApplyInsets(binding.composeView)
        state = readState()
        val actions = SettingsActions(
            onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
            onHoldToSpeedUp = { appPreferences.exoPlayerAllowPressSpeedUp = it },
            onSkipSilence = { appPreferences.skipSilenceEnabled = it },
            onFlatLibrary = { appPreferences.libraryGlobalFlattened = it },
            onDownloadMethod = { method ->
                appPreferences.downloadMethod = method
                // Applies to downloads already waiting too, not just new ones
                downloadManager.applyDownloadMethod(method)
            },
            onRemoveDownloadsOnSignOut = { appPreferences.removeDownloadsOnSignOut = it },
            onSignOut = { lifecycleScope.launch { mainViewModel.logout() } },
        ).afterEach { state = readState() }
        binding.composeView.setContent {
            AppTheme(VaultBrand.AudioVault) {
                state?.let { SettingsScreen(it, actions) }
            }
        }
    }

    private fun readState() = SettingsUiState(
        holdToSpeedUp = appPreferences.exoPlayerAllowPressSpeedUp,
        skipSilence = appPreferences.skipSilenceEnabled,
        flatLibrary = appPreferences.libraryGlobalFlattened,
        downloadMethod = appPreferences.downloadMethod,
        removeDownloadsOnSignOut = appPreferences.removeDownloadsOnSignOut,
        appName = clientInfo.name,
        appVersion = clientInfo.version,
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** Each change is saved, then the screen shows it by reading the settings back. */
private fun SettingsActions.afterEach(refresh: () -> Unit) = copy(
    onHoldToSpeedUp = {
        onHoldToSpeedUp(it)
        refresh()
    },
    onSkipSilence = {
        onSkipSilence(it)
        refresh()
    },
    onFlatLibrary = {
        onFlatLibrary(it)
        refresh()
    },
    onDownloadMethod = {
        onDownloadMethod(it)
        refresh()
    },
    onRemoveDownloadsOnSignOut = {
        onRemoveDownloadsOnSignOut(it)
        refresh()
    },
)

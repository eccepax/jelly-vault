package org.jellyfin.mobile.books.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.databinding.FragmentComposeBinding
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class BookSettingsFragment : Fragment() {

    private var _binding: FragmentComposeBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModel()
    private val appPreferences: BookAppPreferences by inject()
    private val clientInfo: ClientInfo by inject()

    private var state by mutableStateOf<BookSettingsState?>(null)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentComposeBinding.inflate(inflater, container, false)
        return binding.composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val composeView: ComposeView = binding.composeView
        ViewCompat.requestApplyInsets(composeView)
        state = readState()
        val actions = BookSettingsActions(
            onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
            onFontScale = { appPreferences.epubFontScale = it },
            onFontFamily = { appPreferences.epubFontFamily = it },
            onLineSpacing = { appPreferences.epubLineSpacing = it },
            onRemoveDownloadsOnSignOut = { appPreferences.removeDownloadsOnSignOut = it },
            onSignOut = { lifecycleScope.launch { mainViewModel.logout() } },
        ).afterEach { state = readState() }
        composeView.setContent {
            AppTheme(VaultBrand.BookVault) {
                state?.let { BookSettingsScreen(it, actions) }
            }
        }
    }

    private fun readState() = BookSettingsState(
        fontScale = appPreferences.epubFontScale,
        fontFamily = appPreferences.epubFontFamily,
        lineSpacing = appPreferences.epubLineSpacing,
        removeDownloadsOnSignOut = appPreferences.removeDownloadsOnSignOut,
        appName = clientInfo.name,
        appVersion = clientInfo.version,
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** Runs [refresh] after every change, so the screen shows what was saved. */
private fun BookSettingsActions.afterEach(refresh: () -> Unit) = copy(
    onFontScale = {
        onFontScale(it)
        refresh()
    },
    onFontFamily = {
        onFontFamily(it)
        refresh()
    },
    onLineSpacing = {
        onLineSpacing(it)
        refresh()
    },
    onRemoveDownloadsOnSignOut = {
        onRemoveDownloadsOnSignOut(it)
        refresh()
    },
)

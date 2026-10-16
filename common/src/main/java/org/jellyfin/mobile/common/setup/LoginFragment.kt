package org.jellyfin.mobile.common.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.fragment.app.Fragment
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * The sign-in screen for an app that moves between fragments (AudioVault, BookVault); a Compose
 * app shows [LoginRoute] itself. It's in the app's accent, from the app's own `vault_accent`.
 */
class LoginFragment : Fragment() {
    private val mainViewModel: MainViewModel by activityViewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setContent {
                AppTheme(VaultBrand(colorResource(R.color.vault_accent))) {
                    LoginRoute(mainViewModel)
                }
            }
        }
}

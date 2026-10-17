package org.jellyfin.mobile.common.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * The sign-in screen for an app that moves between fragments (AudioVault, BookVault); a Compose
 * app shows [LoginRoute] itself. It's in the app's [VaultBrand], the one its other screens use.
 */
class LoginFragment : Fragment() {
    private val mainViewModel: MainViewModel by activityViewModel()
    private val brand: VaultBrand by inject()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setContent {
                AppTheme(brand) {
                    LoginRoute(mainViewModel)
                }
            }
        }
}

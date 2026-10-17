package org.jellyfin.mobile.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.ui.screens.connect.ConnectScreen
import org.jellyfin.mobile.common.ui.screens.connect.StyledTextButton
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class ConnectFragment : Fragment() {
    private val mainViewModel: MainViewModel by activityViewModel()
    private val activityEventHandler: ActivityEventHandler by inject()
    private var _viewBinding: FragmentComposeBinding? = null
    private val viewBinding get() = _viewBinding!!
    private val composeView: ComposeView get() = viewBinding.composeView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _viewBinding = FragmentComposeBinding.inflate(inflater, container, false)
        return composeView.apply { applyWindowInsetsAsMargins() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply window insets
        ViewCompat.requestApplyInsets(composeView)

        val encounteredConnectionError = arguments?.getBoolean(Constants.FRAGMENT_CONNECT_EXTRA_ERROR) == true

        composeView.setContent {
            AppTheme(VaultBrand.AudioVault) {
                ConnectScreen(
                    mainViewModel = mainViewModel,
                    showExternalConnectionError = encounteredConnectionError,
                    logoRes = R.drawable.app_logo,
                    bottomActions = {
                        StyledTextButton(
                            text = stringResource(R.string.view_downloads),
                            onClick = { activityEventHandler.emit(ActivityEvent.OpenDownloads) },
                        )
                    },
                )
            }
        }
    }
}

package org.jellyfin.mobile.books.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.databinding.FragmentComposeBinding
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.ui.screens.connect.ConnectScreen
import org.jellyfin.mobile.common.ui.utils.AppTheme
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class BookConnectFragment : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModel()
    private var _binding: FragmentComposeBinding? = null
    private val binding get() = _binding!!
    private val composeView: ComposeView get() = binding.composeView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentComposeBinding.inflate(inflater, container, false)
        return composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.requestApplyInsets(composeView)

        val encounteredConnectionError = arguments?.getBoolean(ARG_CONNECTION_ERROR) == true

        composeView.setContent {
            AppTheme {
                ConnectScreen(
                    mainViewModel = mainViewModel,
                    showExternalConnectionError = encounteredConnectionError,
                    logoRes = R.drawable.book_logo,
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_CONNECTION_ERROR = "connection_error"
    }
}

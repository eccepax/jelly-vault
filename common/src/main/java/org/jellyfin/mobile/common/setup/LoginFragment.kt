package org.jellyfin.mobile.common.setup

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.databinding.FragmentLoginBinding
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import timber.log.Timber

private const val LOGIN_TIMEOUT_MS = 30_000L

class LoginFragment : Fragment() {
    private val mainViewModel: MainViewModel by activityViewModel()
    private val apiClient: ApiClient by inject()
    private val apiClientController: ApiClientController by inject()
    private val appPreferences: AppPreferences by inject()

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // The apps draw edge-to-edge, so keep the form clear of the system bars and the keyboard
        val basePadding = Rect(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.updatePadding(
                left = basePadding.left + bars.left,
                top = basePadding.top + bars.top,
                right = basePadding.right + bars.right,
                bottom = basePadding.bottom + bars.bottom,
            )
            insets
        }

        binding.buttonChangeServer.setOnClickListener {
            mainViewModel.resetServer()
        }

        binding.buttonLogin.setOnClickListener {
            doLogin()
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.isVisible = loading
        binding.buttonLogin.isEnabled = !loading
        binding.buttonChangeServer.isEnabled = !loading
        binding.editUsername.isEnabled = !loading
        binding.editPassword.isEnabled = !loading
    }

    private fun setError(message: String?) {
        binding.textError.text = message.orEmpty()
        binding.textError.isVisible = !message.isNullOrBlank()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun doLogin() {
        val username = binding.editUsername.text?.toString()?.trim().orEmpty()
        val password = binding.editPassword.text?.toString()?.trim().orEmpty()

        if (username.isBlank()) {
            setError(getString(R.string.login_error_missing_username))
            return
        }

        val serverId = appPreferences.currentServerId
        if (serverId == null) {
            setError(getString(R.string.login_error_missing_server))
            return
        }

        setError(null)
        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val authResult = withTimeout(LOGIN_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        apiClient.userApi.authenticateUserByName(username, password).content
                    }
                }

                val userId = authResult.user?.id?.toString()
                val accessToken = authResult.accessToken

                if (userId.isNullOrBlank() || accessToken.isNullOrBlank()) {
                    setError(getString(R.string.login_error_invalid_response))
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    apiClientController.setupUser(serverId = serverId, userId = userId, accessToken = accessToken)
                }

                Timber.i("Login success for userId=$userId")
                // Notify the ViewModel; the Activity observes serverState and navigates accordingly.
                mainViewModel.refreshState()
            } catch (e: TimeoutCancellationException) {
                Timber.e(e, "Login timed out")
                setError(getString(R.string.login_error_timeout))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiClientException) {
                Timber.e(e, "Login failed")
                setError(e.message ?: getString(R.string.login_error_failed))
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Login failed (unexpected)")
                setError(getString(R.string.login_error_failed))
            } finally {
                // Leaving the screen cancels the login after the view is gone; there's nothing left to update
                if (_binding != null) setLoading(false)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

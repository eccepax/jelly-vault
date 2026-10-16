package org.jellyfin.mobile.common.setup

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.setup.quickconnect.QuickConnect
import org.jellyfin.mobile.common.setup.quickconnect.QuickConnectState
import org.jellyfin.mobile.common.setup.quickconnect.SignedInUser
import org.jellyfin.mobile.common.setup.quickconnect.quickConnectSignIn
import org.jellyfin.sdk.api.client.exception.ApiClientException
import timber.log.Timber

internal const val LOGIN_TIMEOUT_MS = 30_000L

/** Why signing in didn't work: the server's own reason when it gave one ([detail]), else [res]. */
data class LoginMessage(@StringRes val res: Int, val detail: String? = null)

/** What the sign-in screen shows besides what's being typed. */
data class LoginUiState(
    val busy: Boolean = false,
    val error: LoginMessage? = null,
    /** The Quick Connect code to approve from a signed-in device, while waiting for that. */
    val quickConnectCode: String? = null,
)

/** What the sign-in screen can do. */
data class LoginActions(
    val onSignIn: (username: String, password: String) -> Unit = { _, _ -> },
    val onQuickConnect: () -> Unit = {},
    val onChangeServer: () -> Unit = {},
)

/**
 * Signs in to the chosen server, with a password or Quick Connect, and saves the user for it.
 * Each sign-in calls its onSignedIn once the user is saved, for the app to move on from the screen.
 */
class LoginViewModel(
    private val passwordSignIn: PasswordSignIn,
    private val quickConnect: QuickConnect,
    private val apiClientController: ApiClientController,
    private val appPreferences: AppPreferences,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> get() = _state

    private var quickConnectJob: Job? = null

    fun signIn(username: String, password: String, onSignedIn: suspend () -> Unit) {
        val name = username.trim()
        if (name.isBlank()) return fail(R.string.login_error_missing_username)
        val serverId = appPreferences.currentServerId ?: return fail(R.string.login_error_missing_server)

        _state.value = LoginUiState(busy = true)
        viewModelScope.launch {
            val error = try {
                val user = withTimeout(LOGIN_TIMEOUT_MS) {
                    withContext(io) { passwordSignIn.signIn(name, password.trim()) }
                }
                if (user == null) {
                    LoginMessage(R.string.login_error_invalid_response)
                } else {
                    save(serverId, user, onSignedIn)
                    null
                }
            } catch (e: TimeoutCancellationException) {
                Timber.e(e, "Login timed out")
                LoginMessage(R.string.login_error_timeout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiClientException) {
                Timber.e(e, "Login failed")
                LoginMessage(R.string.login_error_failed, e.message)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Login failed (unexpected)")
                LoginMessage(R.string.login_error_failed)
            }
            _state.value = LoginUiState(error = error)
        }
    }

    /** Shows a code to approve from a signed-in device, then signs in once it's approved. */
    fun startQuickConnect(onSignedIn: suspend () -> Unit) {
        val serverId = appPreferences.currentServerId ?: return fail(R.string.login_error_missing_server)
        _state.value = LoginUiState()
        quickConnectJob?.cancel()
        quickConnectJob = viewModelScope.launch {
            quickConnectSignIn(quickConnect).collect { state ->
                _state.value = when (state) {
                    is QuickConnectState.WaitingForApproval -> LoginUiState(quickConnectCode = state.code)
                    is QuickConnectState.SignedIn -> {
                        save(serverId, state.user, onSignedIn)
                        LoginUiState()
                    }
                    QuickConnectState.Unavailable -> LoginUiState(error = LoginMessage(R.string.login_quick_connect_unavailable))
                    QuickConnectState.Expired -> LoginUiState(error = LoginMessage(R.string.login_quick_connect_expired))
                    QuickConnectState.Failed -> LoginUiState(error = LoginMessage(R.string.login_quick_connect_failed))
                }
            }
        }
    }

    private suspend fun save(serverId: Long, user: SignedInUser, onSignedIn: suspend () -> Unit) {
        withContext(io) { apiClientController.setupUser(serverId, user.userId, user.accessToken) }
        Timber.i("Signed in as userId=${user.userId}")
        onSignedIn()
    }

    private fun fail(@StringRes res: Int) {
        _state.value = LoginUiState(error = LoginMessage(res))
    }
}

package org.jellyfin.mobile.common

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.data.entity.ServerEntity

class MainViewModel(
    app: Application,
    private val apiClientController: ApiClientController,
    private val signOut: SignOut,
) : AndroidViewModel(app) {
    private val _serverState: MutableStateFlow<ServerState> = MutableStateFlow(ServerState.Pending)
    val serverState: StateFlow<ServerState> get() = _serverState

    init {
        viewModelScope.launch {
            refreshState()
        }
    }

    suspend fun switchServer(hostname: String) {
        apiClientController.setupServer(hostname)
        refreshState()
    }

    suspend fun refreshState() {
        val serverEntity = apiClientController.loadSavedServer()
        apiClientController.loadSavedServerUser()
        _serverState.value = serverEntity?.let { entity -> ServerState.Available(entity) } ?: ServerState.Unset
    }

    fun resetServer() {
        _serverState.value = ServerState.Unset
    }

    suspend fun logout() {
        signOut()
        refreshState()
    }
}

sealed class ServerState {
    open val server: ServerEntity? = null

    object Pending : ServerState()
    object Unset : ServerState()
    class Available(override val server: ServerEntity) : ServerState()
}

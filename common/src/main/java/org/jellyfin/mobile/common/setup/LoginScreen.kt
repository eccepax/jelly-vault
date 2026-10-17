package org.jellyfin.mobile.common.setup

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.common.ui.dpadLeavesTextField
import org.jellyfin.mobile.common.ui.screens.connect.StyledTextButton
import org.jellyfin.mobile.common.ui.theme.VaultSpacing
import org.koin.androidx.compose.koinViewModel

/** The sign-in screen, signing in to the server [mainViewModel] has chosen and telling it once that's done. */
@Composable
fun LoginRoute(mainViewModel: MainViewModel, loginViewModel: LoginViewModel = koinViewModel()) {
    val loginState by loginViewModel.state.collectAsState()
    val server by mainViewModel.serverState.collectAsState()
    val hostname = server.server?.hostname
    val insecure = remember(hostname) { hostname?.let(::signsInUnencryptedOverInternet) == true }
    val state = loginState.copy(insecureServer = insecure)
    val activity = LocalActivity.current
    DisposableEffect(loginViewModel) {
        // A rotation comes straight back to this screen, so a code being shown carries on
        onDispose { if (activity?.isChangingConfigurations != true) loginViewModel.leave() }
    }
    val actions = remember(mainViewModel, loginViewModel) {
        LoginActions(
            onSignIn = { username, password -> loginViewModel.signIn(username, password, mainViewModel::refreshState) },
            onQuickConnect = { loginViewModel.startQuickConnect(mainViewModel::refreshState) },
            onChangeServer = mainViewModel::resetServer,
        )
    }
    LoginScreen(state, actions)
}

@Composable
fun LoginScreen(state: LoginUiState, actions: LoginActions) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val signIn = { actions.onSignIn(username, password) }
    val editable = !state.busy

    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        // Scrolls, and keeps clear of the keyboard, so the buttons stay reachable while typing
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(VaultSpacing.XLarge),
        ) {
            Text(stringResource(R.string.login_title), style = MaterialTheme.typography.h5)
            if (state.insecureServer) InsecureServerWarning()
            CredentialFields(
                username = username,
                onUsername = { username = it },
                password = password,
                onPassword = { password = it },
                enabled = editable,
                onDone = signIn,
            )
            state.error?.let { error ->
                Text(
                    text = error.detail ?: stringResource(error.res),
                    color = MaterialTheme.colors.error,
                    modifier = Modifier.padding(top = VaultSpacing.Medium),
                )
            }
            if (state.busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = VaultSpacing.Large)
                        .testTag(VaultTestTags.LOADING),
                )
            }
            Spacer(Modifier.height(VaultSpacing.Large))
            StyledTextButton(text = stringResource(R.string.login_button), enabled = editable, onClick = signIn)
            StyledTextButton(
                text = stringResource(R.string.login_quick_connect),
                enabled = editable,
                onClick = actions.onQuickConnect,
            )
            state.quickConnectCode?.let { code -> QuickConnectCode(code) }
            StyledTextButton(
                text = stringResource(R.string.login_change_server),
                enabled = editable,
                onClick = actions.onChangeServer,
            )
        }
    }
}

/** That the password, and everything after it, would cross the internet unencrypted. */
@Composable
private fun InsecureServerWarning() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = VaultSpacing.Medium)
            .testTag(VaultTestTags.LOGIN_INSECURE_SERVER),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colors.error)
        Text(
            text = stringResource(R.string.login_insecure_server),
            style = MaterialTheme.typography.body2,
            modifier = Modifier.padding(start = VaultSpacing.Medium),
        )
    }
}

/** The username and password; Done on the keyboard signs in. */
@Composable
private fun CredentialFields(
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    enabled: Boolean,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = username,
        onValueChange = onUsername,
        label = { Text(stringResource(R.string.login_username)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = VaultSpacing.Large)
            .testTag(VaultTestTags.LOGIN_USERNAME)
            // A TV remote moves on, rather than getting stuck in the field
            .dpadLeavesTextField(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = onPassword,
        label = { Text(stringResource(R.string.login_password)) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = VaultSpacing.Medium)
            .testTag(VaultTestTags.LOGIN_PASSWORD)
            // A TV remote moves on, rather than getting stuck in the field
            .dpadLeavesTextField(),
    )
}

/** The code to approve, spaced out to read across a room on a TV, with where to enter it. */
@Composable
private fun QuickConnectCode(code: String) {
    Text(
        text = code,
        style = MaterialTheme.typography.h4.copy(letterSpacing = CODE_LETTER_SPACING),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = VaultSpacing.Large)
            .testTag(VaultTestTags.QUICK_CONNECT_CODE),
    )
    Text(
        text = stringResource(R.string.login_quick_connect_hint),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = VaultSpacing.Small),
    )
}

private val CODE_LETTER_SPACING = 0.2.em

package org.jellyfin.mobile.common.ui.screens.connect

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.ui.utils.CenterRow

@Composable
fun ConnectScreen(
    mainViewModel: MainViewModel,
    showExternalConnectionError: Boolean,
    @DrawableRes logoRes: Int,
    bottomActions: @Composable (() -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colors.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            LogoHeader(logoRes = logoRes)
            ServerSelection(
                showExternalConnectionError = showExternalConnectionError,
                onConnected = { hostname ->
                    mainViewModel.switchServer(hostname)
                },
            )
            bottomActions?.invoke()
        }
    }
}

@Stable
@Composable
fun LogoHeader(@DrawableRes logoRes: Int) {
    CenterRow(
        modifier = Modifier.padding(vertical = 25.dp),
    ) {
        Image(
            painter = painterResource(logoRes),
            modifier = Modifier
                .height(72.dp),
            contentDescription = null,
        )
    }
}

@Stable
@Composable
fun StyledTextButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(),
    ) {
        Text(text = text)
    }
}

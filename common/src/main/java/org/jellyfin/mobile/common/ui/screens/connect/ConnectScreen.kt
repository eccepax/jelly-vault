package org.jellyfin.mobile.common.ui.screens.connect

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
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

/** Whether a button is showing its focus outline; for tests. */
internal val FocusOutlined = SemanticsPropertyKey<Boolean>("FocusOutlined")

private val FOCUS_OUTLINE_WIDTH = 3.dp

@Stable
@Composable
fun StyledTextButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // A TV remote moves focus between buttons, so the focused one is outlined. Touch never focuses
    // buttons, so phones never show it.
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val outlineColor = if (focused) Color.White else Color.Transparent
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { this[FocusOutlined] = focused }
            .border(FOCUS_OUTLINE_WIDTH, outlineColor, MaterialTheme.shapes.small),
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(),
    ) {
        Text(text = text)
    }
}

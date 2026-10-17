package org.jellyfin.mobile.common.ui.theme

import androidx.compose.foundation.border
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Whether [focusBorder] is drawing its outline, for tests to check. */
val FocusOutlined = SemanticsPropertyKey<Boolean>("FocusOutlined")

/**
 * Outlines whatever has the D-pad focus. A TV has no touch, so focus must be plain to see; touch
 * never focuses buttons or cards, so phones never show it.
 */
@Composable
fun Modifier.focusBorder(focused: Boolean, shape: Shape = MaterialTheme.shapes.small): Modifier =
    semantics { this[FocusOutlined] = focused }
        .border(FOCUS_OUTLINE_WIDTH, if (focused) VaultColors.OnBackground else Color.Transparent, shape)

private val FOCUS_OUTLINE_WIDTH = 3.dp

package org.jellyfin.mobile.common.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import org.jellyfin.mobile.common.ui.theme.VaultAlpha
import org.jellyfin.mobile.common.ui.theme.VaultSpacing

/** The heading over a run of content on a browsing screen, such as a shelf of albums. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.h6,
        modifier = modifier.padding(
            start = VaultSpacing.Large,
            end = VaultSpacing.Large,
            top = VaultSpacing.Large,
            bottom = VaultSpacing.Small,
        ),
    )
}

/** Text that supports something else: a subtitle, a duration, a note. */
@Composable
fun SecondaryText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.caption,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colors.onBackground.copy(alpha = VaultAlpha.SECONDARY),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

package org.jellyfin.mobile.books.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.app.EpubFontFamily
import org.jellyfin.mobile.common.ui.components.Choice
import org.jellyfin.mobile.common.ui.components.ChoiceDialog
import org.jellyfin.mobile.common.ui.components.SettingsActionRow

/** One of the EPUB text settings, which apply to every book; Settings and the reader both offer them. */
enum class TextSetting { FONT_SCALE, FONT_FAMILY, LINE_SPACING }

/** What changing a text setting does. */
data class TextSettingActions(
    val onFontScale: (Float) -> Unit = {},
    val onFontFamily: (EpubFontFamily) -> Unit = {},
    val onLineSpacing: (Float) -> Unit = {},
)

/** A row per text setting, with its current value; tapping one opens its picker. */
@Composable
fun TextSettingRows(settings: EpubTextSettings, onPick: (TextSetting) -> Unit) {
    SettingsActionRow(
        label = stringResource(R.string.text_settings_font_size),
        summary = formatFontScaleLabel(settings.fontScale),
        onClick = { onPick(TextSetting.FONT_SCALE) },
    )
    SettingsActionRow(
        label = stringResource(R.string.text_settings_font_family),
        summary = formatFontFamilyLabel(settings.fontFamily),
        onClick = { onPick(TextSetting.FONT_FAMILY) },
    )
    SettingsActionRow(
        label = stringResource(R.string.text_settings_line_spacing),
        summary = formatLineSpacingLabel(settings.lineSpacing),
        onClick = { onPick(TextSetting.LINE_SPACING) },
    )
}

/** The presets for [setting], with the current one selected. */
@Composable
fun TextSettingPicker(
    setting: TextSetting,
    settings: EpubTextSettings,
    actions: TextSettingActions,
    onDismiss: () -> Unit,
) {
    when (setting) {
        TextSetting.FONT_SCALE -> ChoiceDialog(
            title = stringResource(R.string.text_settings_font_size),
            choices = EPUB_FONT_SCALE_PRESETS.map { Choice(it, formatFontScaleLabel(it)) },
            selected = settings.fontScale,
            onSelect = actions.onFontScale,
            onDismiss = onDismiss,
        )
        TextSetting.FONT_FAMILY -> ChoiceDialog(
            title = stringResource(R.string.text_settings_font_family),
            choices = EPUB_FONT_FAMILY_PRESETS.map { Choice(it, formatFontFamilyLabel(it)) },
            selected = settings.fontFamily,
            onSelect = actions.onFontFamily,
            onDismiss = onDismiss,
        )
        TextSetting.LINE_SPACING -> ChoiceDialog(
            title = stringResource(R.string.text_settings_line_spacing),
            choices = EPUB_LINE_SPACING_PRESETS.map { Choice(it, formatLineSpacingLabel(it)) },
            selected = settings.lineSpacing,
            onSelect = actions.onLineSpacing,
            onDismiss = onDismiss,
        )
    }
}

package org.jellyfin.mobile.common.ui.components

/** One option to pick from, in a [SettingsRadioGroup] or a [ChoiceDialog]. */
data class Choice<T>(val value: T, val label: String, val detail: String? = null)

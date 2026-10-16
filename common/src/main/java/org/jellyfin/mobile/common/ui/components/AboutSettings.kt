package org.jellyfin.mobile.common.ui.components

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.ui.theme.VaultSpacing

/**
 * Settings' About section: which app and version this is, and a way to report a bug that fills
 * both in (and the [device]) on the GitHub form, so reports say what they're about.
 */
@Composable
fun AboutSettings(appName: String, version: String, device: String = deviceDescription()) {
    val uriHandler = LocalUriHandler.current
    SettingsSectionHeader(stringResource(R.string.settings_about))
    Text(
        stringResource(R.string.about_app_version, appName, version),
        modifier = Modifier.padding(horizontal = VaultSpacing.Large, vertical = VaultSpacing.Small),
    )
    SettingsActionRow(
        label = stringResource(R.string.report_bug),
        summary = stringResource(R.string.report_bug_summary),
        onClick = { uriHandler.openUri(bugReportUrl(appName, version, device)) },
    )
}

/** GitHub's bug form for the apps, with what the app knows filled in; the keys are the form's field ids. */
fun bugReportUrl(appName: String, version: String, device: String = deviceDescription()): String =
    Uri.parse(ISSUES_NEW_URL).buildUpon()
        .appendQueryParameter("template", "bug-report.yaml")
        .appendQueryParameter("app", appName)
        .appendQueryParameter("app-version", version)
        .appendQueryParameter("device", device)
        .build()
        .toString()

/** E.g. "Google Pixel 10 Pro, Android 16". */
fun deviceDescription(): String = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"

private const val ISSUES_NEW_URL = "https://github.com/eccepax/jelly-vault/issues/new"

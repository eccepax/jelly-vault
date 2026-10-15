package org.jellyfin.mobile.common.account

import org.jellyfin.mobile.common.app.AppPreferences

/**
 * What happens to downloads when signing out: they stay on the device, unless the user turned on
 * "Remove downloads when signing out" in Settings ([AppPreferences.removeDownloadsOnSignOut]).
 * Run from [SignOut]'s forgetAccount step.
 */
class DownloadsOnSignOut(
    private val preferences: AppPreferences,
    private val removeAll: suspend () -> Unit,
) {
    suspend operator fun invoke() {
        if (preferences.removeDownloadsOnSignOut) removeAll()
    }
}

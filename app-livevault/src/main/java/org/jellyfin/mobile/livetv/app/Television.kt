package org.jellyfin.mobile.livetv.app

import android.content.Context
import android.content.res.Configuration

/**
 * Whether LiveVault runs on a TV (Google TV): its UI mode, which every Android TV has. One check
 * for everything that differs there (no casting, no floating window, the TV channel list), so they
 * never disagree on a device.
 */
val Configuration.isTelevision: Boolean
    get() = uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

val Context.isTelevision: Boolean get() = resources.configuration.isTelevision

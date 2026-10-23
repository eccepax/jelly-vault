package org.jellyfin.mobile.utils

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService

val Context.isLowRamDevice: Boolean
    get() = getSystemService<ActivityManager>()!!.isLowRamDevice

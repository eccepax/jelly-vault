@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils.extensions

import androidx.annotation.CheckResult

@CheckResult
inline fun Int.hasFlag(flag: Int) = this and flag == flag

package org.jellyfin.mobile.music.ui

import java.util.Locale

private const val KB = 1_000.0
private const val MB = KB * 1_000
private const val GB = MB * 1_000

/** A storage size the way Android's own settings count it (1 GB = 1000³ bytes). */
fun formatBytes(bytes: Long): String = when {
    bytes >= GB -> String.format(Locale.ROOT, "%.1f GB", bytes / GB).replace(".0 ", " ")
    bytes >= MB -> String.format(Locale.ROOT, "%.0f MB", bytes / MB)
    else -> String.format(Locale.ROOT, "%.0f KB", bytes / KB)
}

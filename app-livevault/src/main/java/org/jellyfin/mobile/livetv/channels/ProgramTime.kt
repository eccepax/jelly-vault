package org.jellyfin.mobile.livetv.channels

import org.jellyfin.mobile.livetv.domain.LiveChannel
import org.jellyfin.mobile.livetv.domain.LiveProgram
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** How much of the program has aired at [now], from 0 (not started) to 1 (over). */
fun LiveProgram.progressAt(now: Instant): Float {
    val length = Duration.between(start, end).toMillis()
    if (length <= 0) return 1f
    return (Duration.between(start, now).toMillis().toFloat() / length).coerceIn(0f, 1f)
}

/** When the program ends, as a short local time ("4:00 PM"). */
fun LiveProgram.endTimeText(zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(end.atZone(zone))

/** The name to show: free (Tubi) channels lose the "FREE| " prefix the Vault gives them. */
val LiveChannel.displayName: String
    get() = name.removePrefix(FREE_PREFIX).trim().ifEmpty { name }

private const val FREE_PREFIX = "FREE|"

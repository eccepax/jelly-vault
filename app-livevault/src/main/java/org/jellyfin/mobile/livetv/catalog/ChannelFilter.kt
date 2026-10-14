package org.jellyfin.mobile.livetv.catalog

import org.jellyfin.mobile.livetv.domain.LiveChannel

/** What the channel browser narrows the channel list to. Every part must match. */
data class ChannelFilter(
    /** Only channels carrying this tag, or every channel when null. */
    val tag: String? = null,
    val favoritesOnly: Boolean = false,
    /** Matches channel names and what's on now (ignoring case), or the start of a channel number. */
    val query: String = "",
)

fun List<LiveChannel>.filteredBy(filter: ChannelFilter): List<LiveChannel> {
    val query = filter.query.trim()
    return filter { channel ->
        (filter.tag == null || filter.tag in channel.tags) &&
            (!filter.favoritesOnly || channel.isFavorite) &&
            (query.isEmpty() || channel.matches(query))
    }
}

private fun LiveChannel.matches(query: String): Boolean =
    name.contains(query, ignoreCase = true) ||
        nowPlaying?.title?.contains(query, ignoreCase = true) == true ||
        number.startsWith(query)

/** Every tag in use on these channels, once each, sorted. */
fun List<LiveChannel>.tags(): List<String> =
    flatMap { it.tags }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)

/**
 * Tuner order: numerically by each dot-separated part, so "4" < "4.1" < "4.2" < "4.10" < "5" < "10".
 * Numbers that aren't numeric sort after the numbered ones, alphabetically.
 */
val channelNumberOrder: Comparator<String> = Comparator { a, b ->
    val partsA = a.numericParts()
    val partsB = b.numericParts()
    when {
        partsA == null && partsB == null -> a.compareTo(b)
        partsA == null -> 1
        partsB == null -> -1
        else -> compareParts(partsA, partsB)
    }
}

private fun String.numericParts(): List<Long>? =
    split('.').map { it.toLongOrNull() ?: return null }

private fun compareParts(a: List<Long>, b: List<Long>): Int {
    a.zip(b).forEach { (x, y) -> if (x != y) return x.compareTo(y) }
    return a.size.compareTo(b.size)
}

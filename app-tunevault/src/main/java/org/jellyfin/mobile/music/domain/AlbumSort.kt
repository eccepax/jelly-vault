package org.jellyfin.mobile.music.domain

private val LEADING_ARTICLE = Regex("""^(the|a|an)\s+(?=\S)""")

/**
 * How music apps alphabetise: case-insensitive, ignoring leading punctuation and a leading
 * "The"/"A"/"An" ("The Purple Bird" files under P, "\"Weird Al\"" under W).
 */
fun sortKey(text: String): String {
    val lowered = text.trim().lowercase().trimStart { !it.isLetterOrDigit() }
    return lowered.replaceFirst(LEADING_ARTICLE, "")
}

private val byTitle = compareBy<Album> { sortKey(it.title) }

enum class AlbumSort(private val comparator: Comparator<Album>) {
    TITLE(byTitle),

    /** Artist A–Z (unknown artists last), each artist's albums oldest first. */
    ARTIST(
        compareBy<Album> { it.artist.isBlank() }
            .thenBy { sortKey(it.artist) }
            .thenBy(nullsLast()) { it.year }
            .then(byTitle),
    ),

    /** Newest first; undated albums last. */
    YEAR(compareBy<Album, Int?>(nullsLast(reverseOrder())) { it.year }.then(byTitle)),

    /** Most recently added to the server first. */
    RECENTLY_ADDED(compareBy<Album, java.time.Instant?>(nullsLast(reverseOrder())) { it.dateAdded }.then(byTitle)),
    ;

    fun sort(albums: List<Album>): List<Album> = albums.sortedWith(comparator)
}

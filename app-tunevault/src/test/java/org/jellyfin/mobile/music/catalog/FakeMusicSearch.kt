package org.jellyfin.mobile.music.catalog

import kotlinx.coroutines.CompletableDeferred

/** Answers with [results] for known queries, [SearchResults.EMPTY] otherwise; records every query. */
class FakeMusicSearch(var results: Map<String, SearchResults> = emptyMap()) : MusicSearch {
    val queries = mutableListOf<String>()
    var failure: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun search(query: String): SearchResults {
        queries += query
        gate?.await()
        failure?.let { throw it }
        return results[query] ?: SearchResults.EMPTY
    }
}

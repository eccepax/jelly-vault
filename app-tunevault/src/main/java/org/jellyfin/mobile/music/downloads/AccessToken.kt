package org.jellyfin.mobile.music.downloads

private const val TOKEN_PARAM = "api_key"

/**
 * [uri] carrying [token] (replacing any it had), or no token at all when signed out. Downloads
 * store their URIs without one and get the current token added to each request as it's made.
 */
fun withAccessToken(uri: String, token: String?): String {
    val queryStart = uri.indexOf('?')
    val base = if (queryStart < 0) uri else uri.substring(0, queryStart)
    val params = if (queryStart < 0) emptyList() else uri.substring(queryStart + 1).split('&')
    val kept = params.filter { it.isNotEmpty() && !it.startsWith("$TOKEN_PARAM=") }
    val all = if (token == null) kept else kept + "$TOKEN_PARAM=$token"
    return if (all.isEmpty()) base else "$base?${all.joinToString("&")}"
}

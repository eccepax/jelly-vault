package org.jellyfin.mobile.common.server

import java.util.UUID

private const val TOKEN_PARAM = "ApiKey"

// The older form: Jellyfin 12 answers 401 to it, and URIs stored by older versions may carry one.
private const val LEGACY_TOKEN_PARAM = "api_key"

/**
 * [uri] carrying [token] (replacing any it had), or no token at all when signed out. For requests
 * that can't send auth headers (players, download managers, plain HTTP clients); stored URIs can
 * be kept without one and get the current token added as each request is made.
 */
fun withAccessToken(uri: String, token: String?): String {
    val queryStart = uri.indexOf('?')
    val base = if (queryStart < 0) uri else uri.substring(0, queryStart)
    val params = if (queryStart < 0) emptyList() else uri.substring(queryStart + 1).split('&')
    val kept = params.filter {
        it.isNotEmpty() && !it.startsWith("$TOKEN_PARAM=") && !it.startsWith("$LEGACY_TOKEN_PARAM=")
    }
    val all = if (token == null) kept else kept + "$TOKEN_PARAM=$token"
    return if (all.isEmpty()) base else "$base?${all.joinToString("&")}"
}

/** The original file of [itemId], with the item id in the server's dashless form. */
fun itemDownloadUrl(baseUrl: String, itemId: UUID, token: String?): String {
    val hexId = itemId.toString().replace("-", "")
    return withAccessToken("${baseUrl.trimEnd('/')}/Items/$hexId/Download", token)
}

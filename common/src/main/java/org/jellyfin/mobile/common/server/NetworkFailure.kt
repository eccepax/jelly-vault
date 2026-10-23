package org.jellyfin.mobile.common.server

import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import java.io.IOException
import java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

/** No route to the server, as opposed to the server answering with an error. */
fun Throwable.isNetworkFailure(): Boolean = when (this) {
    is InvalidStatusException -> false
    is IOException, is ApiClientException -> true
    else -> false
}

private const val HTTP_TOO_MANY_REQUESTS = 429

/** Statuses from a server not ready for a request just now, rather than refusing it as such. */
private val NOT_READY = setOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN, HTTP_CLIENT_TIMEOUT, HTTP_TOO_MANY_REQUESTS)

/**
 * Whether a request that failed with this is worth sending again later: the server couldn't be
 * reached, or wasn't ready for it (signed out, overloaded, or restarting behind a proxy).
 */
fun Throwable.worthRetrying(): Boolean = when (this) {
    is InvalidStatusException -> status in NOT_READY || status >= HTTP_INTERNAL_ERROR
    else -> isNetworkFailure()
}

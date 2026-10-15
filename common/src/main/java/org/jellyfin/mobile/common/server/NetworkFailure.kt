package org.jellyfin.mobile.common.server

import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import java.io.IOException

/** No route to the server, as opposed to the server answering with an error. */
fun Throwable.isNetworkFailure(): Boolean = when (this) {
    is InvalidStatusException -> false
    is IOException, is ApiClientException -> true
    else -> false
}

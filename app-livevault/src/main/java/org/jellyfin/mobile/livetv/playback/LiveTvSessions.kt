package org.jellyfin.mobile.livetv.playback

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.livetv.domain.channelNameUsesProviderLogin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.model.api.BaseItemKind

/** Another device on the server watching a live channel. */
data class ChannelSession(
    val deviceName: String,
    val userName: String,
    val channelName: String,
)

/** Who else is watching live TV through the server. */
interface LiveTvSessions {
    /** Every other device's session that's playing a live channel. */
    suspend fun othersWatching(): List<ChannelSession>
}

/** The ones taking the provider login: watching a channel that isn't free. */
fun List<ChannelSession>.holdingLogin(): List<ChannelSession> =
    filter { channelNameUsesProviderLogin(it.channelName) }

/**
 * [LiveTvSessions] from the server's session list. It only knows the server's own viewers: the
 * login can also be in use outside it (the provider's app, a leaked stream).
 */
class JellyfinLiveTvSessions(
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LiveTvSessions {

    override suspend fun othersWatching(): List<ChannelSession> = withContext(ioDispatcher) {
        apiClient.sessionApi.getSessions().content
            .filter { it.deviceId != apiClient.deviceInfo.id }
            .mapNotNull { session ->
                val item = session.nowPlayingItem?.takeIf { it.type == BaseItemKind.TV_CHANNEL }
                    ?: return@mapNotNull null
                ChannelSession(
                    deviceName = session.deviceName.orEmpty(),
                    userName = session.userName.orEmpty(),
                    channelName = item.name.orEmpty(),
                )
            }
    }
}

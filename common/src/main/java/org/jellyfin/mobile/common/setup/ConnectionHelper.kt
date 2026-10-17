package org.jellyfin.mobile.common.setup

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.ui.state.CheckUrlState
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.discovery.LocalServerDiscovery
import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.discovery.RecommendedServerIssue
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import timber.log.Timber

class ConnectionHelper(
    private val context: Context,
    private val jellyfin: Jellyfin,
) {
    @Suppress("LongMethod")
    suspend fun checkServerUrl(enteredUrl: String): CheckUrlState {
        Timber.i("checkServerUrlAndConnection $enteredUrl")

        val candidates = withContext(Dispatchers.IO) {
            jellyfin.discovery.getAddressCandidates(enteredUrl)
        }
        Timber.i("Address candidates are $candidates")

        val recommendedServers = withContext(Dispatchers.IO) {
            jellyfin.discovery.getRecommendedServers(candidates)
        }
        val badServers = recommendedServers.filter {
            it.score == RecommendedServerInfoScore.OK || it.score == RecommendedServerInfoScore.BAD
        }

        val server = pickBestServer(recommendedServers)
        if (server != null) {
            val systemInfo = requireNotNull(server.systemInfo)
            val serverVersion = systemInfo.getOrNull()?.version
            Timber.i("Found valid server at ${server.address} with rating ${server.score} and version $serverVersion")
            return CheckUrlState.Success(server.address)
        }

        val loggedServers = badServers.joinToString { "${it.address}/${it.systemInfo}" }
        Timber.i("No valid servers found, invalid candidates were: $loggedServers")

        val tooOld = tooOldServerVersion(recommendedServers)
        val error = when {
            tooOld != null -> context.getString(
                R.string.connection_error_server_too_old,
                tooOld.display(),
                Jellyfin.minimumVersion.let { "${it.major}.${it.minor}" },
            )
            badServers.isNotEmpty() -> {
                val count = badServers.size
                val (unreachableServers, incompatibleServers) = badServers.partition { result -> result.systemInfo.getOrNull() == null }

                StringBuilder().apply {
                    append(context.resources.getQuantityString(R.plurals.connection_error_prefix, count, count))
                    if (unreachableServers.isNotEmpty()) {
                        append("\n\n")
                        append(context.getString(R.string.connection_error_unable_to_reach_sever))
                        append(":\n")
                        append(
                            unreachableServers.joinToString(separator = "\n") { result ->
                                "· ${result.address}"
                            },
                        )
                    }
                    if (incompatibleServers.isNotEmpty()) {
                        append("\n\n")
                        append(context.getString(R.string.connection_error_unsupported_version_or_product))
                        append(":\n")
                        append(
                            incompatibleServers.joinToString(separator = "\n") { result ->
                                "· ${result.address}"
                            },
                        )
                    }
                }.toString()
            }
            else -> null
        }

        return CheckUrlState.Error(error)
    }

    fun discoverServersAsFlow(): Flow<ServerDiscoveryInfo> =
        jellyfin.discovery
            .discoverLocalServers(maxServers = LocalServerDiscovery.DISCOVERY_MAX_SERVERS)
            .flowOn(Dispatchers.IO)
}

/**
 * The version of a candidate that answered but runs a Jellyfin older than the SDK supports, else
 * `null`. Such a server would sign in, then fail every request it can't decode.
 */
internal fun tooOldServerVersion(candidates: Collection<RecommendedServerInfo>): ServerVersion? =
    candidates.firstNotNullOfOrNull { candidate ->
        candidate.issues.filterIsInstance<RecommendedServerIssue.UnsupportedServerVersion>().firstOrNull()?.version
    }

private fun ServerVersion.display() = "$major.$minor.$patch"

/** Picks the best-rated server: the first GREAT candidate, else the first GOOD one, else `null`. */
internal fun pickBestServer(candidates: Collection<RecommendedServerInfo>): RecommendedServerInfo? =
    candidates.firstOrNull { it.score == RecommendedServerInfoScore.GREAT }
        ?: candidates.firstOrNull { it.score == RecommendedServerInfoScore.GOOD }

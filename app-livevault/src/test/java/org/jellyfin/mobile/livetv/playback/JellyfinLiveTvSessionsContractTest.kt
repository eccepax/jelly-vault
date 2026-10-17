package org.jellyfin.mobile.livetv.playback

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.livetv.catalog.JellyfinLiveTvCatalog
import org.jellyfin.mobile.livetv.domain.LiveChannel
import org.jellyfin.mobile.testing.VaultFixtures.LiveTv
import org.jellyfin.mobile.testing.VaultTestServer
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.serializer.toUUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Reads the Vault's sessions. A second "viewer" client REPORTS watching a free channel without
 * opening it: no stream is opened, so no tuner and never the provider login is used.
 */
class JellyfinLiveTvSessionsContractTest {

    private lateinit var viewer: ApiClient
    private lateinit var channel: LiveChannel

    @BeforeEach
    fun setUp() = runBlocking<Unit> {
        VaultTestServer.assumeAvailable()
        channel = JellyfinLiveTvCatalog(VaultTestServer.apiClient).channels()
            .filter { LiveTv.FREE_TAG in it.tags && it.name.startsWith(LiveTv.FREE_PREFIX) }
            .shouldNotBeEmpty()
            .first()
        viewer = VaultTestServer.newSignedInClient(deviceId = VIEWER_DEVICE)
        viewer.playStateApi.reportPlaybackStart(
            PlaybackStartInfo(
                itemId = channel.id.toUUID(),
                playMethod = PlayMethod.DIRECT_STREAM,
                canSeek = false,
                isPaused = false,
                isMuted = false,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    @AfterEach
    fun signOutViewer() = runBlocking<Unit> {
        if (::viewer.isInitialized) viewer.sessionApi.reportSessionEnded()
    }

    @Test
    fun `lists another device watching a channel`() = runBlocking<Unit> {
        val sessions = JellyfinLiveTvSessions(VaultTestServer.apiClient).othersWatching()

        sessions.map { it.channelName } shouldContain channel.name
        sessions.map { it.userName } shouldContain VaultTestServer.username
    }

    @Test
    fun `leaves out this device`() = runBlocking<Unit> {
        val sessions = JellyfinLiveTvSessions(viewer).othersWatching()

        sessions.map { it.channelName } shouldNotContain channel.name
    }

    private companion object {
        const val VIEWER_DEVICE = "livevault-contract-viewer"
    }
}

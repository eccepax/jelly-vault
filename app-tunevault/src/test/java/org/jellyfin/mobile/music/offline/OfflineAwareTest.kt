package org.jellyfin.mobile.music.offline

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.music.catalog.FakeFavoritesStore
import org.jellyfin.mobile.music.catalog.FakeMusicCatalog
import org.jellyfin.mobile.music.catalog.album
import org.jellyfin.mobile.music.domain.track
import org.jellyfin.mobile.music.downloads.DownloadedCollection
import org.junit.jupiter.api.Test
import java.net.ConnectException

/** The app's catalog: the server's, or the downloads' once the server can't be reached. */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineAwareTest {

    private val server = FakeMusicCatalog(albums = listOf(album("pb", "The Purple Bird"), album("hw", "Homework")))
    private val library = DownloadedLibrary(
        MutableStateFlow(
            listOf(
                DownloadedCollection("hw", DownloadedCollection.Kind.ALBUM, "Homework", null, null, listOf(track("a"))),
            ),
        ),
        MutableStateFlow(setOf("a")),
    )

    private fun TestScope.catalog() =
        OfflineAwareMusicCatalog(server, library, ServerConnectivity(ping = { false }, scope = backgroundScope))

    @Test
    fun `online, it's the server's library`() = runTest {
        catalog().albums().map { it.id } shouldBe listOf("pb", "hw")
    }

    @Test
    fun `offline, it's the downloads`() = runTest {
        server.failure = ConnectException("refused")

        catalog().albums().map { it.id } shouldBe listOf("hw")
    }

    @Test
    fun `stream urls always come from the server's catalog, so downloads are found by id`() = runTest {
        server.failure = ConnectException("refused")

        catalog().streamUrl("a") shouldBe server.streamUrl("a")
    }

    @Test
    fun `offline there are no favorites to show, rather than an error`() = runTest {
        val server = FakeFavoritesStore(albums = listOf(album("pb", "The Purple Bird")), tracks = listOf(track("a")))
        val favorites = OfflineAwareFavoritesStore(
            server,
            ServerConnectivity(ping = { false }, scope = backgroundScope),
        )
        favorites.favoriteAlbums().map { it.id } shouldBe listOf("pb")

        server.failure = ConnectException("refused")

        favorites.favoriteAlbums() shouldBe emptyList()
        favorites.favoriteTracks() shouldBe emptyList()
    }
}

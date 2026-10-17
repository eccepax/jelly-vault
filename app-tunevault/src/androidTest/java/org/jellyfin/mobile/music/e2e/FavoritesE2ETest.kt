package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.music.catalog.FavoritesStore
import org.jellyfin.mobile.music.catalog.MusicCatalog
import org.jellyfin.mobile.music.nowplaying.MiniPlayerTags
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.testing.VaultFixtures.PatternSuite
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.serializer.toUUID
import org.junit.After
import org.junit.Test

private const val PLAYBACK_TIMEOUT_MS = 30_000L

/** Hearts against the test server. Writes favorite flags and restores them afterwards. */
@CollectionsArea
class FavoritesE2ETest : LoggedInE2ETest() {

    private val apiClient: ApiClient get() = koinGet()
    private val originals = mutableMapOf<String, Boolean>()

    @After
    fun restoreFavorites() = runBlocking<Unit> {
        val store: FavoritesStore = koinGet()
        originals.forEach { (id, favorite) -> store.setFavorite(id, favorite) }
    }

    private fun isFavoriteOnServer(id: String): Boolean = runBlocking {
        apiClient.itemsApi.getItems(ids = listOf(id.toUUID())).content.items.single().userData?.isFavorite == true
    }

    /** Remembers [id]'s favorite flag for teardown and makes sure it starts unfavorited. */
    private fun startUnfavorited(id: String) {
        originals[id] = isFavoriteOnServer(id)
        runBlocking { koinGet<FavoritesStore>().setFavorite(id, false) }
    }

    private fun patternSuite() = runBlocking {
        val catalog: MusicCatalog = koinGet()
        val album = catalog.albums().first { it.title == PatternSuite.TITLE }
        album to catalog.albumTracks(album.id).first { it.title == PatternSuite.KNOWN_SONG }
    }

    @Test
    fun theNowPlayingHeartSavesTheSongAsAFavorite() {
        connectAndLoginToLibrary()
        val (_, knownSong) = patternSuite()
        startUnfavorited(knownSong.id)
        openAlbum(PatternSuite.TITLE, firstTrack = PatternSuite.FIRST_TRACK)
        composeRule.onNodeWithText(PatternSuite.KNOWN_SONG).performClick()
        val playback: PlaybackController = koinGet()
        composeRule.waitUntil(PLAYBACK_TIMEOUT_MS) { onMain { playback.state.value.isPlaying } }
        composeRule.onNodeWithTag(MiniPlayerTags.ROOT).performClick()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            runCatching { composeRule.onNodeWithContentDescription("Add to favorites").assertExists() }.isSuccess
        }

        composeRule.onNodeWithContentDescription("Add to favorites").performClick()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) { isFavoriteOnServer(knownSong.id) }
        composeRule.onNodeWithContentDescription("Remove from favorites").assertExists()
    }

    @Test
    fun aFavoritedAlbumShowsOnHome() {
        connectAndLoginToLibrary()
        val (album, _) = patternSuite()
        startUnfavorited(album.id)
        openAlbum(PatternSuite.TITLE, firstTrack = PatternSuite.FIRST_TRACK)

        composeRule.onNodeWithContentDescription("Add to favorites").performClick()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { isFavoriteOnServer(album.id) }

        composeRule.onNode(hasText("Home") and isSelectable()).performClick()
        waitForComposeText("Recently played")
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            runCatching {
                composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Favorite albums"))
            }.isSuccess
        }
        composeRule.onNodeWithText("Favorite albums").assertExists()
    }
}

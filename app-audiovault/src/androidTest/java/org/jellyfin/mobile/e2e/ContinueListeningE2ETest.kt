package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.testing.VaultFixtures.Audiobooks
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.junit.After
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

/**
 * A book the server has a place in, half a minute in, shows under "Continue
 * listening" against the test server.
 *
 * The place is put on the server directly, as listening a few minutes in would leave it: the test
 * server's books are too short for Jellyfin to keep a place from the app's reports (it keeps none in
 * a track's first or last five minutes).
 */
@BrowseArea
class ContinueListeningE2ETest : LoggedInE2ETest() {

    private var started: UUID? = null

    @After
    fun forgetThePlace() {
        val track = started ?: return
        elsewhere { it.playStateApi.markUnplayedItem(track) }
    }

    @Test
    fun aBookTheServerHasAPlaceInShowsUnderContinueListening() {
        val title = Audiobooks.M4B
        started = elsewhere { api ->
            val book = api.itemsApi.getItems(
                includeItemTypes = listOf(BaseItemKind.AUDIO_BOOK),
                recursive = true,
                searchTerm = Audiobooks.M4B_SEARCH_TERM,
            ).content.items.orEmpty().single()
            val id = requireNotNull(book.id)
            api.itemsApi.updateItemUserData(
                itemId = id,
                data = UpdateUserItemDataDto(playbackPositionTicks = HALF_A_MINUTE_TICKS, lastPlayedDate = LocalDateTime.now()),
            )
            id
        }

        loginAndWaitForLibrary()

        val inContinueListening =
            hasText(title) and hasAnyAncestor(hasTestTag(VaultTestTags.CONTINUE_LISTENING))
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodes(inContinueListening, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun <T> elsewhere(block: suspend (ApiClient) -> T): T = asTestUserElsewhere(OTHER_DEVICE_ID, block)

    private companion object {
        const val OTHER_DEVICE_ID = "vault-e2e-audiovault-elsewhere"
        const val HALF_A_MINUTE_TICKS = 30 * 10_000_000L
    }
}

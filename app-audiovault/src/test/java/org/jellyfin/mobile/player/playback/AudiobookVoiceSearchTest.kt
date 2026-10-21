package org.jellyfin.mobile.player.playback

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.jupiter.api.Test
import java.util.UUID

/** "Play [book] on AudioVault": the server's best match, ready to play. */
class AudiobookVoiceSearchTest {

    private val book = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private var reachable = true
    private val matches = mutableMapOf("lighthouse keepers" to book)

    private val search = AudiobookVoiceSearch(
        topMatch = { query ->
            if (!reachable) throw ApiClientException("No route to host")
            matches[query]
        },
        loader = AudiobookQueueLoader(
            items = { ids ->
                ids.map {
                    BaseItemDto(
                        id = it,
                        type = BaseItemKind.AUDIO_BOOK,
                        name = "Lighthouse Keepers",
                    )
                }
            },
            playable = { null },
            coverUrl = { null },
        ),
    )

    @Test
    fun `the best match is the book that plays`() = runTest {
        search.bookFor("lighthouse keepers")?.tracks?.map { it.title } shouldBe listOf("Lighthouse Keepers")
    }

    @Test
    fun `no match, no book`() = runTest {
        search.bookFor("nonexistent") shouldBe null
    }

    @Test
    fun `with the server out of reach, no book`() = runTest {
        reachable = false

        search.bookFor("lighthouse keepers") shouldBe null
    }
}

package org.jellyfin.mobile.books.progress

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.extensions.ticks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** How a book's place is written into the user's play data; the server's side is BookProgressContractTest's. */
class JellyfinBookProgressServerTest {

    private val apiClient = mockk<ApiClient>()
    private val itemsApi = mockk<ItemsApi>()
    private val sent = slot<UpdateUserItemDataDto>()
    private val book = UUID.fromString("3b832b64-bc56-ccf9-15d9-c608962c3acd")
    private val noon = Instant.parse("2026-10-21T12:00:00Z")

    @BeforeEach
    fun setUp() {
        // The api client's *Api properties are extensions with real logic; stub them directly.
        mockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
        every { apiClient.itemsApi } returns itemsApi
        coEvery { itemsApi.updateItemUserData(itemId = book, data = capture(sent)) } returns
            Response(mockk<UserItemDataDto>(), 200, emptyMap())
    }

    @AfterEach
    fun tearDown() = unmockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")

    @Test
    fun `a place partway through is unplayed, so a finished book read again from the start is unread`() = runTest {
        JellyfinBookProgressServer(apiClient).record(book, 0L.ticks, finished = false, at = noon)

        sent.captured.playbackPositionTicks shouldBe 0L
        sent.captured.played shouldBe false
    }

    @Test
    fun `a finished book keeps its place at the last page`() = runTest {
        JellyfinBookProgressServer(apiClient).record(book, 9_000_000L.ticks, finished = true, at = noon)

        sent.captured.playbackPositionTicks shouldBe 9_000_000L
        sent.captured.played shouldBe true
    }
}

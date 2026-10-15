package org.jellyfin.mobile.player.source

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.operations.MediaInfoApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.extensions.inWholeTicks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MediaSourceResolverTest {

    /** [effectiveStartTime], the resume-position fallback used when resolving a stream. */
    @Nested
    inner class StartTime {

        @Test
        fun `explicit start time wins over the server-stored position`() {
            effectiveStartTime(startTime = 30.seconds, playbackPositionTicks = 10_000_000L) shouldBe 30.seconds
        }

        @Test
        fun `falls back to a positive server-stored position when no explicit start time is given`() {
            effectiveStartTime(startTime = null, playbackPositionTicks = 50_000_000L) shouldBe 5.seconds
        }

        @Test
        fun `zero ticks is treated as no saved position, not a valid zero start time`() {
            effectiveStartTime(startTime = null, playbackPositionTicks = 0L) shouldBe null
        }

        @Test
        fun `negative ticks is treated as no saved position`() {
            effectiveStartTime(startTime = null, playbackPositionTicks = -10L) shouldBe null
        }

        @Test
        fun `missing server value with no explicit start time yields null`() {
            effectiveStartTime(startTime = null, playbackPositionTicks = null) shouldBe null
        }

        @Test
        fun `explicit zero duration is honored as-is, not treated as absent`() {
            effectiveStartTime(startTime = Duration.ZERO, playbackPositionTicks = 50_000_000L) shouldBe Duration.ZERO
        }
    }

    /** [MediaSourceResolver.resolveMediaSource]: what it asks the server for and what it makes of the answer. */
    @Nested
    inner class Resolving {

        private val apiClient = mockk<ApiClient>()
        private val userLibraryApi = mockk<UserLibraryApi>()
        private val mediaInfoApi = mockk<MediaInfoApi>()

        private val itemId = UUID.randomUUID()
        private val ownSourceId = itemId.toString().replace("-", "")

        @BeforeEach
        fun setUp() {
            // The api client's *Api properties are extensions with real logic; stub them directly.
            mockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
            every { apiClient.userLibraryApi } returns userLibraryApi
            every { apiClient.mediaInfoApi } returns mediaInfoApi
            serverHas(item(savedPosition = null))
            serverOffers(PlaybackInfoResponse(mediaSources = listOf(source(ownSourceId)), playSessionId = "session-1"))
        }

        @AfterEach
        fun tearDown() {
            unmockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
        }

        private fun resolve(startTime: Duration? = null) = runBlocking {
            MediaSourceResolver(apiClient).resolveMediaSource(itemId, startTime = startTime)
        }

        @Test
        fun `resumes where the server says the listener left off`() {
            serverHas(item(savedPosition = 12.minutes))

            val source = resolve().getOrThrow()

            source.startTime shouldBe 12.minutes
            coVerify {
                mediaInfoApi.getPostedPlaybackInfo(
                    itemId,
                    match { it.startTimeTicks == 12.minutes.inWholeTicks },
                )
            }
        }

        @Test
        fun `an explicit start time wins over the server's saved position`() {
            serverHas(item(savedPosition = 12.minutes))

            val source = resolve(startTime = 3.minutes).getOrThrow()

            source.startTime shouldBe 3.minutes
        }

        @Test
        fun `still plays, from the start, when the item's details can't be loaded`() {
            coEvery { userLibraryApi.getItem(itemId, any()) } throws InvalidStatusException(500)

            val source = resolve().getOrThrow()

            source.startTime shouldBe Duration.ZERO
            source.item shouldBe null
        }

        @Test
        fun `asks for the item's own media source when the caller doesn't name one`() {
            resolve()

            coVerify { mediaInfoApi.getPostedPlaybackInfo(itemId, match { it.mediaSourceId == ownSourceId }) }
        }

        @Test
        fun `picks the item's own media source over others the server offers`() {
            serverOffers(
                PlaybackInfoResponse(
                    mediaSources = listOf(source("some-other-version"), source(ownSourceId)),
                    playSessionId = "session-1",
                ),
            )

            resolve().getOrThrow().id shouldBe ownSourceId
        }

        @Test
        fun `falls back to the first media source when none is the item's own`() {
            serverOffers(
                PlaybackInfoResponse(
                    mediaSources = listOf(source("version-a"), source("version-b")),
                    playSessionId = "s",
                ),
            )

            resolve().getOrThrow().id shouldBe "version-a"
        }

        @Test
        fun `keeps the server's play session for the reports that follow`() {
            resolve().getOrThrow().playSessionId shouldBe "session-1"
        }

        @Test
        fun `a server that can't be reached is a network failure`() {
            coEvery { mediaInfoApi.getPostedPlaybackInfo(any(), any()) } throws InvalidStatusException(503)

            resolve().exceptionOrNull().shouldBeInstanceOf<PlayerException.NetworkFailure>()
        }

        @Test
        fun `an answer without a play session is unsupported content`() {
            serverOffers(PlaybackInfoResponse(mediaSources = listOf(source(ownSourceId)), playSessionId = null))

            resolve().exceptionOrNull().shouldBeInstanceOf<PlayerException.UnsupportedContent>()
        }

        @Test
        fun `an answer without any media source is unsupported content`() {
            serverOffers(PlaybackInfoResponse(mediaSources = emptyList(), playSessionId = "session-1"))

            resolve().exceptionOrNull().shouldBeInstanceOf<PlayerException.UnsupportedContent>()
        }

        @Test
        fun `a media source the device can't play in any way is unsupported content`() {
            serverOffers(
                PlaybackInfoResponse(mediaSources = listOf(source(ownSourceId, playable = false)), playSessionId = "s"),
            )

            resolve().exceptionOrNull().shouldBeInstanceOf<PlayerException.UnsupportedContent>()
        }

        private fun serverHas(item: BaseItemDto) {
            coEvery { userLibraryApi.getItem(itemId, any()) } returns Response(item, 200, emptyMap())
        }

        private fun serverOffers(response: PlaybackInfoResponse) {
            coEvery { mediaInfoApi.getPostedPlaybackInfo(itemId, any()) } returns Response(response, 200, emptyMap())
        }

        private fun item(savedPosition: Duration?) = mockk<BaseItemDto>(relaxed = true) {
            every { userData } returns savedPosition?.let { position ->
                mockk<UserItemDataDto>(relaxed = true) { every { playbackPositionTicks } returns position.inWholeTicks }
            }
        }

        private fun source(id: String, playable: Boolean = true) = mockk<MediaSourceInfo>(relaxed = true) {
            every { this@mockk.id } returns id
            every { supportsDirectPlay } returns playable
            every { supportsDirectStream } returns false
            every { supportsTranscoding } returns false
            every { mediaStreams } returns emptyList()
            every { liveStreamId } returns null
        }
    }
}

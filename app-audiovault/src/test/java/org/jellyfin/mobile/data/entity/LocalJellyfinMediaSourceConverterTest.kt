package org.jellyfin.mobile.data.entity

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How a downloaded track's details are stored in the database. */
class LocalJellyfinMediaSourceConverterTest {

    private val converter = LocalJellyfinMediaSourceConverter()

    @Test
    fun `where the listener left a downloaded track is kept`() {
        val source = source().apply { startTime = 12.minutes + 5.seconds }

        val stored = converter.toLocalJellyfinMediaSource(converter.fromLocalJellyfinMediaSource(source))

        stored.startTime shouldBe 12.minutes + 5.seconds
    }

    @Test
    fun `listening the server hasn't heard about yet is kept, with when it happened`() {
        val savedAt = Instant.parse("2026-10-15T08:30:00Z")
        val source = source().apply {
            positionSavedAt = savedAt
            finished = true
        }

        val stored = converter.toLocalJellyfinMediaSource(converter.fromLocalJellyfinMediaSource(source))

        stored.positionSavedAt shouldBe savedAt
        stored.finished shouldBe true
    }

    @Test
    fun `a track with nothing waiting for the server stays that way`() {
        val stored = converter.toLocalJellyfinMediaSource(converter.fromLocalJellyfinMediaSource(source()))

        stored.positionSavedAt shouldBe null
        stored.finished shouldBe false
    }

    @Test
    fun `everything else about the track is kept too`() {
        val source = source()

        val stored = converter.toLocalJellyfinMediaSource(converter.fromLocalJellyfinMediaSource(source))

        stored.itemId shouldBe source.itemId
        stored.sourceInfo shouldBe source.sourceInfo
        stored.playSessionId shouldBe source.playSessionId
        stored.localDirectoryUri shouldBe source.localDirectoryUri
        stored.remoteFileUri shouldBe source.remoteFileUri
        stored.downloadSize shouldBe source.downloadSize
    }

    @Test
    fun `a track saved before positions were kept loads, starting from the beginning`() {
        val saved = Json.parseToJsonElement(converter.fromLocalJellyfinMediaSource(source())).jsonObject
        val older = Json.encodeToString(
            JsonObject(
                saved.filterKeys {
                    it !in setOf("startTime", "positionSavedAt", "finished")
                },
            ),
        )

        converter.toLocalJellyfinMediaSource(older).startTime shouldBe Duration.ZERO
        converter.toLocalJellyfinMediaSource(older).positionSavedAt shouldBe null
    }

    private fun source() = LocalJellyfinMediaSource(
        itemId = UUID.randomUUID(),
        item = null,
        sourceInfo = MediaSourceInfo(
            protocol = MediaProtocol.HTTP, id = "src", path = null, encoderPath = null, encoderProtocol = null,
            type = MediaSourceType.DEFAULT, container = "m4b", size = null, name = "Chapter", isRemote = false,
            eTag = null, runTimeTicks = null, readAtNativeFramerate = false, ignoreDts = false, ignoreIndex = false,
            genPtsInput = false, supportsTranscoding = false, supportsDirectStream = false, supportsDirectPlay = true,
            isInfiniteStream = false, useMostCompatibleTranscodingProfile = false, requiresOpening = false,
            openToken = null, requiresClosing = false, liveStreamId = null, bufferMs = null, requiresLooping = false,
            supportsProbing = false, videoType = null, isoType = null, video3dFormat = null, mediaStreams = emptyList(),
            mediaAttachments = emptyList(), formats = emptyList(), bitrate = null, fallbackMaxStreamingBitrate = null,
            timestamp = null, requiredHttpHeaders = emptyMap(), transcodingUrl = null,
            transcodingSubProtocol = MediaStreamProtocol.HTTP, transcodingContainer = null, analyzeDurationMs = null,
            defaultAudioStreamIndex = null, defaultSubtitleStreamIndex = null, hasSegments = false,
        ),
        playSessionId = "session",
        localDirectoryUri = "/downloads",
        remoteFileUri = "https://vault/Audio/x/stream",
        downloadSize = 1234,
    )
}

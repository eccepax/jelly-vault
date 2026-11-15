package org.jellyfin.mobile.player.source

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.PlayMethod
import org.junit.jupiter.api.Test
import java.util.UUID

/** How a source plays (its play method) and what it's called. */
class JellyfinMediaSourceTest {

    private fun source(
        // A valid play method by default, so a test about something else needn't give one
        supportsDirectPlay: Boolean = true,
        supportsDirectStream: Boolean = false,
        supportsTranscoding: Boolean = false,
    ): MediaSourceInfo = MediaSourceInfo(
        protocol = MediaProtocol.HTTP,
        id = "src-id",
        path = null,
        encoderPath = null,
        encoderProtocol = null,
        type = MediaSourceType.DEFAULT,
        container = null,
        size = null,
        name = "src-name",
        isRemote = false,
        eTag = null,
        runTimeTicks = null,
        readAtNativeFramerate = false,
        ignoreDts = false,
        ignoreIndex = false,
        genPtsInput = false,
        supportsTranscoding = supportsTranscoding,
        supportsDirectStream = supportsDirectStream,
        supportsDirectPlay = supportsDirectPlay,
        isInfiniteStream = false,
        useMostCompatibleTranscodingProfile = false,
        requiresOpening = false,
        openToken = null,
        requiresClosing = false,
        liveStreamId = null,
        bufferMs = null,
        requiresLooping = false,
        supportsProbing = false,
        videoType = null,
        isoType = null,
        video3dFormat = null,
        mediaStreams = emptyList(),
        mediaAttachments = emptyList(),
        formats = emptyList(),
        bitrate = null,
        fallbackMaxStreamingBitrate = null,
        timestamp = null,
        requiredHttpHeaders = emptyMap(),
        transcodingUrl = null,
        transcodingSubProtocol = MediaStreamProtocol.HTTP,
        transcodingContainer = null,
        analyzeDurationMs = null,
        defaultAudioStreamIndex = null,
        defaultSubtitleStreamIndex = null,
        hasSegments = false,
    )

    private fun mediaSource(
        sourceInfo: MediaSourceInfo,
    ) = RemoteJellyfinMediaSource(
        itemId = UUID.randomUUID(),
        item = null,
        sourceInfo = sourceInfo,
        playSessionId = "session",
        maxStreamingBitrate = null,
        startTime = null,
    )

    // --- name ---------------------------------------------------------------------------------

    @Test
    fun `a source's name is its track's, or the source's own without the track's details`() {
        mediaSource(source()).name shouldBe "src-name"
    }

    // --- playMethod --------------------------------------------------------------------------

    @Test
    fun `direct play takes priority when multiple methods are supported`() {
        val source =
            mediaSource(source(supportsDirectPlay = true, supportsDirectStream = true, supportsTranscoding = true))
        source.playMethod shouldBe PlayMethod.DIRECT_PLAY
    }

    @Test
    fun `falls back to direct stream when direct play is unsupported`() {
        val source =
            mediaSource(source(supportsDirectPlay = false, supportsDirectStream = true, supportsTranscoding = true))
        source.playMethod shouldBe PlayMethod.DIRECT_STREAM
    }

    @Test
    fun `falls back to transcode when only transcoding is supported`() {
        val source =
            mediaSource(source(supportsDirectPlay = false, supportsDirectStream = false, supportsTranscoding = true))
        source.playMethod shouldBe PlayMethod.TRANSCODE
    }

    @Test
    fun `throws when no play method is supported`() {
        val info = source(supportsDirectPlay = false, supportsDirectStream = false, supportsTranscoding = false)
        shouldThrow<IllegalArgumentException> { mediaSource(info) }
    }
}

package org.jellyfin.mobile.player.source

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.player.deviceprofile.CodecHelpers
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.junit.jupiter.api.Test
import java.util.UUID

/** Basic functionality coverage for stream classification/selection and play-method resolution. */
class JellyfinMediaSourceTest {

    private fun stream(
        index: Int,
        type: MediaStreamType,
        codec: String? = null,
        deliveryMethod: SubtitleDeliveryMethod? = null,
        deliveryUrl: String? = null,
    ): MediaStream = MediaStream(
        index = index,
        type = type,
        codec = codec,
        isExternal = false,
        isTextSubtitleStream = false,
        deliveryMethod = deliveryMethod,
        deliveryUrl = deliveryUrl,
        isInterlaced = false,
        isDefault = false,
        isForced = false,
        isHearingImpaired = false,
        supportsExternalStream = false,
    )

    private fun source(
        mediaStreams: List<MediaStream> = emptyList(),
        defaultAudioStreamIndex: Int? = null,
        defaultSubtitleStreamIndex: Int? = null,
        // Default to a valid play method so stream-classification tests (which don't care about
        // playMethod) don't have to specify one just to avoid the "no play method" exception.
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
        mediaStreams = mediaStreams,
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
        defaultAudioStreamIndex = defaultAudioStreamIndex,
        defaultSubtitleStreamIndex = defaultSubtitleStreamIndex,
        hasSegments = false,
    )

    private fun mediaSource(
        sourceInfo: MediaSourceInfo,
        playbackDetails: PlaybackDetails? = null,
    ) = RemoteJellyfinMediaSource(
        itemId = UUID.randomUUID(),
        item = null,
        sourceInfo = sourceInfo,
        playSessionId = "session",
        liveStreamId = null,
        maxStreamingBitrate = null,
        playbackDetails = playbackDetails,
    )

    // --- stream classification -----------------------------------------------------------------

    @Test
    fun `no streams yields empty stream lists and no selections`() {
        val source = mediaSource(source())
        source.audioStreams shouldBe emptyList()
        source.subtitleStreams shouldBe emptyList()
        source.selectedVideoStream shouldBe null
        source.selectedAudioStream shouldBe null
    }

    @Test
    fun `first video stream is selected when multiple are present`() {
        val v1 = stream(0, MediaStreamType.VIDEO)
        val v2 = stream(1, MediaStreamType.VIDEO)
        val source = mediaSource(source(mediaStreams = listOf(v1, v2)))
        source.selectedVideoStream shouldBe v1
    }

    @Test
    fun `audio stream selection uses the explicit playbackDetails index over the source default`() {
        val a0 = stream(0, MediaStreamType.AUDIO)
        val a1 = stream(1, MediaStreamType.AUDIO)
        val source = mediaSource(
            source(mediaStreams = listOf(a0, a1), defaultAudioStreamIndex = 0),
            playbackDetails = PlaybackDetails(startTime = null, audioStreamIndex = 1, subtitleStreamIndex = null),
        )
        source.selectedAudioStream shouldBe a1
    }

    @Test
    fun `audio stream selection falls back to the source default index when playbackDetails has none`() {
        val a0 = stream(0, MediaStreamType.AUDIO)
        val a1 = stream(1, MediaStreamType.AUDIO)
        val source = mediaSource(source(mediaStreams = listOf(a0, a1), defaultAudioStreamIndex = 1))
        source.selectedAudioStream shouldBe a1
    }

    @Test
    fun `external subtitle is included when delivery method is EXTERNAL with a URL and recognized codec`() {
        val sub =
            stream(
                0,
                MediaStreamType.SUBTITLE,
                codec = "srt",
                deliveryMethod = SubtitleDeliveryMethod.EXTERNAL,
                deliveryUrl = "http://x/sub.srt",
            )
        val source = mediaSource(source(mediaStreams = listOf(sub)))
        source.externalSubtitleStreams shouldBe listOf(
            ExternalSubtitleStream(
                index = 0,
                deliveryUrl = "http://x/sub.srt",
                mimeType = requireNotNull(CodecHelpers.getSubtitleMimeType("srt")),
                displayTitle = "",
                language = Constants.LANGUAGE_UNDEFINED,
            ),
        )
    }

    @Test
    fun `external subtitle is excluded when the codec has no known mime type mapping`() {
        val sub =
            stream(
                0,
                MediaStreamType.SUBTITLE,
                codec = "unknown-codec",
                deliveryMethod = SubtitleDeliveryMethod.EXTERNAL,
                deliveryUrl = "http://x/sub.xyz",
            )
        val source = mediaSource(source(mediaStreams = listOf(sub)))
        source.externalSubtitleStreams shouldBe emptyList()
    }

    @Test
    fun `non-external subtitle delivery method does not produce an external subtitle stream`() {
        val sub = stream(0, MediaStreamType.SUBTITLE, codec = "srt", deliveryMethod = SubtitleDeliveryMethod.EMBED)
        val source = mediaSource(source(mediaStreams = listOf(sub)))
        source.externalSubtitleStreams shouldBe emptyList()
    }

    @Test
    fun `embedded image, data, and lyric stream types are ignored entirely`() {
        val streams = listOf(
            stream(0, MediaStreamType.EMBEDDED_IMAGE),
            stream(1, MediaStreamType.DATA),
            stream(2, MediaStreamType.LYRIC),
        )
        val source = mediaSource(source(mediaStreams = streams))
        source.audioStreams shouldBe emptyList()
        source.subtitleStreams shouldBe emptyList()
        source.selectedVideoStream shouldBe null
    }

    @Test
    fun `selectedSubtitleStreamIndex defaults to -1 when nothing is selected`() {
        val source = mediaSource(source())
        source.selectedSubtitleStreamIndex shouldBe -1
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

package org.jellyfin.mobile.player.deviceprofile

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import androidx.media3.common.MimeTypes
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Spot-check coverage for [CodecHelpers]'s Android codec constant → Jellyfin/FFmpeg-style string
 * mapping tables. These reference `android.media.MediaCodecInfo.CodecProfileLevel`/`MediaFormat`
 * *constants* (compile-time-inlined), not method calls, so they resolve fine against the JVM-test
 * android.jar stub unlike most other android.media usage in this codebase.
 */
class CodecHelpersTest {

    @Test
    fun `maps known video codec mime types to their ffmpeg-style names`() {
        CodecHelpers.getVideoCodec(MediaFormat.MIMETYPE_VIDEO_AVC) shouldBe "h264"
        CodecHelpers.getVideoCodec(MediaFormat.MIMETYPE_VIDEO_HEVC) shouldBe "hevc"
        CodecHelpers.getVideoCodec(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION) shouldBe "hevc"
    }

    @Test
    fun `unmapped video mime type returns null`() {
        CodecHelpers.getVideoCodec("video/unknown") shouldBe null
    }

    @Test
    fun `maps known audio codec mime types, and explicitly unsupported ones to null`() {
        CodecHelpers.getAudioCodec(MediaFormat.MIMETYPE_AUDIO_AAC) shouldBe "aac"
        CodecHelpers.getAudioCodec(MediaFormat.MIMETYPE_AUDIO_FLAC) shouldBe "flac"
        // QCELP/MSGSM/G711 are explicitly matched but intentionally unsupported (map to null)
        CodecHelpers.getAudioCodec(MediaFormat.MIMETYPE_AUDIO_QCELP) shouldBe null
        CodecHelpers.getAudioCodec("audio/unknown") shouldBe null
    }

    @Test
    fun `AVC (h264) profile mapping`() {
        CodecHelpers.getVideoProfile("h264", CodecProfileLevel.AVCProfileHigh) shouldBe "high"
        CodecHelpers.getVideoProfile("h264", CodecProfileLevel.AVCProfileBaseline) shouldBe "baseline"
    }

    @Test
    fun `AVC (h264) level mapping, including the explicitly-unsupported level 1b`() {
        CodecHelpers.getVideoLevel("h264", CodecProfileLevel.AVCLevel41) shouldBe 41
        CodecHelpers.getVideoLevel("h264", CodecProfileLevel.AVCLevel1b) shouldBe null
    }

    @Test
    fun `HEVC profile mapping`() {
        CodecHelpers.getVideoProfile("hevc", CodecProfileLevel.HEVCProfileMain10) shouldBe "Main 10"
    }

    @Test
    fun `HEVC level mapping collapses Main and High tiers to the same level string`() {
        val mainTier = CodecHelpers.getVideoLevel("hevc", CodecProfileLevel.HEVCMainTierLevel4)
        val highTier = CodecHelpers.getVideoLevel("hevc", CodecProfileLevel.HEVCHighTierLevel4)
        mainTier shouldBe 120
        highTier shouldBe mainTier
    }

    @Test
    fun `unrecognized codec name for profile or level lookup returns null`() {
        CodecHelpers.getVideoProfile("not-a-codec", 0) shouldBe null
        CodecHelpers.getVideoLevel("not-a-codec", 0) shouldBe null
    }

    @Test
    fun `AAC audio profile mapping`() {
        CodecHelpers.getAudioProfile("aac", CodecProfileLevel.AACObjectLC) shouldBe "LC"
        CodecHelpers.getAudioProfile("aac", CodecProfileLevel.AACObjectHE) shouldBe "HE-AAC"
    }

    @Test
    fun `audio profile lookup for a non-aac codec returns null`() {
        CodecHelpers.getAudioProfile("mp3", CodecProfileLevel.AACObjectLC) shouldBe null
    }

    @Test
    fun `subtitle codec to mime type mapping, including unknown codecs`() {
        CodecHelpers.getSubtitleMimeType("srt") shouldBe MimeTypes.APPLICATION_SUBRIP
        CodecHelpers.getSubtitleMimeType("ass") shouldBe MimeTypes.TEXT_SSA
        CodecHelpers.getSubtitleMimeType("ssa") shouldBe MimeTypes.TEXT_SSA
        CodecHelpers.getSubtitleMimeType("unknown-codec") shouldBe null
        CodecHelpers.getSubtitleMimeType(null) shouldBe null
    }
}

package org.jellyfin.mobile.player.deviceprofile

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Basic functionality coverage for [DeviceCodec.Video]/[DeviceCodec.Audio]'s `mergeCodec`, which
 * combines capability info reported by multiple `MediaCodecInfo` entries for the same codec name.
 *
 * `levels`/`maxChannels`/`maxSampleRate` are private with no public accessor, so only the
 * publicly-observable `profiles`/`maxBitrate` (declared on the [DeviceCodec] base class) are
 * asserted on here.
 */
class DeviceCodecTest {

    @Test
    fun `video merge unions profiles and takes the max bitrate`() {
        val a = DeviceCodec.Video(
            name = "h264",
            mimeType = "video/avc",
            profiles = setOf("baseline"),
            levels = setOf(31),
            maxBitrate = 1_000_000,
        )
        val b = DeviceCodec.Video(
            name = "h264",
            mimeType = "video/avc",
            profiles = setOf("high"),
            levels = setOf(41),
            maxBitrate = 2_000_000,
        )

        val merged = a.mergeCodec(b)

        merged.profiles shouldContainExactlyInAnyOrder listOf("baseline", "high")
        merged.maxBitrate shouldBe 2_000_000
    }

    @Test
    fun `video merge with overlapping profiles does not duplicate them`() {
        val a = DeviceCodec.Video(
            name = "h264",
            mimeType = "video/avc",
            profiles = setOf("high"),
            levels = setOf(31),
            maxBitrate = 1_000_000,
        )
        val b = DeviceCodec.Video(
            name = "h264",
            mimeType = "video/avc",
            profiles = setOf("high"),
            levels = setOf(31),
            maxBitrate = 1_000_000,
        )

        a.mergeCodec(b).profiles shouldBe setOf("high")
    }

    @Test
    fun `audio merge unions profiles and takes the max of bitrate and channels`() {
        val a = DeviceCodec.Audio(
            name = "aac",
            mimeType = "audio/mp4a-latm",
            profiles = setOf("LC"),
            maxBitrate = 128_000,
            maxChannels = 2,
            maxSampleRate = 44_100,
        )
        val b = DeviceCodec.Audio(
            name = "aac",
            mimeType = "audio/mp4a-latm",
            profiles = setOf("HE-AAC"),
            maxBitrate = 256_000,
            maxChannels = 6,
            maxSampleRate = 48_000,
        )

        val merged = a.mergeCodec(b)

        merged.profiles shouldContainExactlyInAnyOrder listOf("LC", "HE-AAC")
        merged.maxBitrate shouldBe 256_000
    }

    @Test
    fun `audio merge is order-independent for bitrate`() {
        val higher = DeviceCodec.Audio(
            name = "aac",
            mimeType = "audio/mp4a-latm",
            profiles = setOf("LC"),
            maxBitrate = 256_000,
            maxChannels = 2,
            maxSampleRate = 44_100,
        )
        val lower = DeviceCodec.Audio(
            name = "aac",
            mimeType = "audio/mp4a-latm",
            profiles = setOf("LC"),
            maxBitrate = 128_000,
            maxChannels = 2,
            maxSampleRate = 44_100,
        )

        higher.mergeCodec(lower).maxBitrate shouldBe 256_000
        lower.mergeCodec(higher).maxBitrate shouldBe 256_000
    }
}

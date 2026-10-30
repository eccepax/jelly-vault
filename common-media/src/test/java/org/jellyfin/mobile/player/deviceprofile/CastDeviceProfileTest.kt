package org.jellyfin.mobile.player.deviceprofile

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileCondition
import org.jellyfin.sdk.model.api.ProfileConditionType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.junit.jupiter.api.Test

/** What a cast device (Google's Default Media Receiver) is offered for live TV. */
class CastDeviceProfileTest {

    private val profile = castVideoProfile("LiveVault")

    @Test
    fun `the profile carries the app's name`() {
        profile.name shouldBe "LiveVault"
    }

    @Test
    fun `nothing is played directly, since a cast device can't play the tuner's raw MPEG-TS`() {
        profile.directPlayProfiles.shouldBeEmpty()
    }

    @Test
    fun `everything comes as H264 with AAC or MP3 over HLS`() {
        val hls = profile.transcodingProfiles.single()

        hls.type shouldBe DlnaProfileType.VIDEO
        hls.protocol shouldBe MediaStreamProtocol.HLS
        hls.container shouldBe "ts"
        hls.videoCodec shouldBe "h264"
        hls.audioCodec.split(",") shouldBe listOf("aac", "mp3")
    }

    @Test
    fun `audio comes in stereo, which every cast device plays`() {
        profile.transcodingProfiles.single().maxAudioChannels shouldBe "2"
    }

    @Test
    fun `only H264 a cast device decodes is copied as it is`() {
        // First-generation Chromecasts decode High profile up to level 4.1 (1080p30), and no
        // interlaced video: anything else is transcoded.
        val h264 = profile.codecProfiles.single { it.type == CodecType.VIDEO }

        h264.codec shouldBe "h264"
        h264.conditions shouldContain ProfileCondition(
            condition = ProfileConditionType.EQUALS_ANY,
            property = ProfileConditionValue.VIDEO_PROFILE,
            value = "high|main|baseline|constrained baseline",
            isRequired = false,
        )
        h264.conditions shouldContain ProfileCondition(
            condition = ProfileConditionType.LESS_THAN_EQUAL,
            property = ProfileConditionValue.VIDEO_LEVEL,
            value = "41",
            isRequired = false,
        )
        h264.conditions shouldContain ProfileCondition(
            condition = ProfileConditionType.NOT_EQUALS,
            property = ProfileConditionValue.IS_INTERLACED,
            value = "true",
            isRequired = false,
        )
    }

    @Test
    fun `the stream stays within what a cast device takes over Wi-Fi`() {
        profile.maxStreamingBitrate.shouldNotBeNull() shouldBe 20_000_000
    }
}

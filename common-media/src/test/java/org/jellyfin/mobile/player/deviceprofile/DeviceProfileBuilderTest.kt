package org.jellyfin.mobile.player.deviceprofile

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileConditionType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.junit.jupiter.api.Test

class DeviceProfileBuilderTest {

    private val noDecoders = DeviceDecoders(video = emptyMap(), audio = emptySet())
    private val h264Only = DeviceDecoders(video = mapOf("h264" to setOf("high", "main")), audio = setOf("aac", "mp3"))

    private fun profile(name: String = "Test", includeVideo: Boolean, decoders: DeviceDecoders) =
        DeviceProfileBuilder(name, includeVideo) { decoders }.getDeviceProfile()

    @Test
    fun `an audio-only profile is exactly AudioVault's`() {
        // Recorded from AudioVault's own builder (Robolectric: no decoders reported, so only the
        // forced codecs), before it moved here.
        val golden = javaClass.getResource("/audio-only-profile.json").shouldNotBeNull().readText()

        profile("AudioVault", includeVideo = false, decoders = noDecoders) shouldBe
            Json.decodeFromString(DeviceProfile.serializer(), golden)
    }

    @Test
    fun `m4b audiobooks with AAC audio are direct played`() {
        // Jellyfin names MP4 audio by its file extension, so an .m4b is container "m4b", not "mp4";
        // without that name every audiobook was transcoded to an unseekable MP3 stream.
        profile(includeVideo = false, decoders = noDecoders).directPlays(container = "m4b", audioCodec = "aac") shouldBe true
    }

    @Test
    fun `m4a files with AAC audio are direct played`() {
        profile(includeVideo = false, decoders = noDecoders).directPlays(container = "m4a", audioCodec = "aac") shouldBe true
    }

    @Test
    fun `an audio-only profile never offers video`() {
        val profile = profile(includeVideo = false, decoders = h264Only)

        profile.directPlayProfiles.map { it.type } shouldNotContain DlnaProfileType.VIDEO
        profile.transcodingProfiles.map { it.type } shouldNotContain DlnaProfileType.VIDEO
        profile.codecProfiles.shouldBeEmpty()
    }

    @Test
    fun `the profile carries the app's name`() {
        profile("LiveVault", includeVideo = true, decoders = h264Only).name shouldBe "LiveVault"
    }

    @Test
    fun `video plays MPEG-TS directly with the device's decoders`() {
        // Live TV from an HDHomeRun tuner is MPEG-TS.
        val ts = profile(includeVideo = true, decoders = h264Only).directPlayProfiles
            .single { it.type == DlnaProfileType.VIDEO && it.container == "mpegts" }

        ts.videoCodec shouldBe "h264"
        ts.audioCodec.shouldNotBeNull().split(",") shouldContain "aac"
    }

    @Test
    fun `video the device can't decode is not direct played`() {
        val videoCodecs = profile(includeVideo = true, decoders = h264Only).directPlayProfiles
            .filter { it.type == DlnaProfileType.VIDEO }
            .flatMap { it.videoCodec.orEmpty().split(",") }

        videoCodecs shouldNotContain "hevc"
    }

    @Test
    fun `a device with no video decoders direct plays no video`() {
        profile(includeVideo = true, decoders = noDecoders).directPlayProfiles.map { it.type } shouldNotContain
            DlnaProfileType.VIDEO
    }

    @Test
    fun `anything else is transcoded to H264 over HLS first`() {
        val first = profile(includeVideo = true, decoders = h264Only).transcodingProfiles.first()

        first.type shouldBe DlnaProfileType.VIDEO
        first.protocol shouldBe MediaStreamProtocol.HLS
        first.container shouldBe "ts"
        first.videoCodec shouldBe "h264"
    }

    @Test
    fun `video direct play is limited to the profiles the decoder supports`() {
        val h264 = profile(includeVideo = true, decoders = h264Only).codecProfiles
            .first { it.codec == "h264" && it.container == "mpegts" }
        val condition = h264.conditions.single()

        condition.condition shouldBe ProfileConditionType.EQUALS_ANY
        condition.property shouldBe ProfileConditionValue.VIDEO_PROFILE
        condition.value.shouldNotBeNull().split("|").toSet() shouldBe setOf("high", "main")
    }

    private fun DeviceProfile.directPlays(container: String, audioCodec: String): Boolean =
        directPlayProfiles.any { profile ->
            profile.type == DlnaProfileType.AUDIO &&
                container in profile.container.orEmpty().split(',') &&
                audioCodec in profile.audioCodec.orEmpty().split(',')
        }
}

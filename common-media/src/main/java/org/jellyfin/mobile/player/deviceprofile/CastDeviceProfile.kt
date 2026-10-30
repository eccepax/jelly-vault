package org.jellyfin.mobile.player.deviceprofile

import org.jellyfin.sdk.model.api.CodecProfile
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileCondition
import org.jellyfin.sdk.model.api.ProfileConditionType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.TranscodingProfile

/**
 * The [DeviceProfile] for video sent to a cast device (Google's Default Media Receiver), from
 * Google's supported media list. Nothing direct: the receiver plays HLS but not the tuner's raw
 * MPEG-TS, so everything comes over HLS as H.264 with AAC or MP3 in stereo. H.264 the oldest cast
 * devices decode (High profile up to level 4.1, progressive) is copied as it is; anything else is
 * transcoded.
 */
fun castVideoProfile(name: String): DeviceProfile = DeviceProfile(
    name = name,
    directPlayProfiles = emptyList(),
    transcodingProfiles = listOf(
        TranscodingProfile(
            type = DlnaProfileType.VIDEO,
            container = "ts",
            videoCodec = "h264",
            audioCodec = "aac,mp3",
            protocol = MediaStreamProtocol.HLS,
            maxAudioChannels = "2",
            conditions = emptyList(),
        ),
    ),
    containerProfiles = emptyList(),
    codecProfiles = listOf(
        CodecProfile(
            type = CodecType.VIDEO,
            codec = "h264",
            applyConditions = emptyList(),
            conditions = listOf(
                condition(
                    ProfileConditionType.EQUALS_ANY,
                    ProfileConditionValue.VIDEO_PROFILE,
                    "high|main|baseline|constrained baseline",
                ),
                condition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.VIDEO_LEVEL, "41"),
                condition(ProfileConditionType.NOT_EQUALS, ProfileConditionValue.IS_INTERLACED, "true"),
            ),
        ),
    ),
    subtitleProfiles = emptyList(),
    maxStreamingBitrate = CAST_MAX_STREAMING_BITRATE,
    maxStaticBitrate = CAST_MAX_STREAMING_BITRATE,
)

private fun condition(type: ProfileConditionType, property: ProfileConditionValue, value: String) =
    ProfileCondition(condition = type, property = property, value = value, isRequired = false)

private const val CAST_MAX_STREAMING_BITRATE = 20_000_000

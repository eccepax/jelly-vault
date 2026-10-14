package org.jellyfin.mobile.player.deviceprofile

import android.media.MediaCodecList
import org.jellyfin.sdk.model.api.CodecProfile
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.ContainerProfile
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileCondition
import org.jellyfin.sdk.model.api.ProfileConditionType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodingProfile

/** The decoders a device has: video codec names (with the profiles each decodes) and audio codec names. */
data class DeviceDecoders(
    val video: Map<String, Set<String>>,
    val audio: Set<String>,
) {
    companion object {
        /** What [MediaCodecList] reports for this device. */
        fun fromDevice(): DeviceDecoders {
            val videoCodecs = HashMap<String, DeviceCodec.Video>()
            val audioCodecs = HashMap<String, DeviceCodec.Audio>()
            for (codecInfo in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
                if (codecInfo.isEncoder) continue
                for (mimeType in codecInfo.supportedTypes) {
                    when (val codec = DeviceCodec.from(codecInfo.getCapabilitiesForType(mimeType))) {
                        is DeviceCodec.Video -> videoCodecs.merge(codec.name, codec, DeviceCodec.Video::mergeCodec)
                        is DeviceCodec.Audio -> audioCodecs.merge(codec.name, codec, DeviceCodec.Audio::mergeCodec)
                        null -> Unit
                    }
                }
            }
            return DeviceDecoders(
                video = videoCodecs.mapValues { (_, codec) -> codec.profiles },
                audio = audioCodecs.keys,
            )
        }
    }
}

/**
 * The [DeviceProfile] a vault app reports to the server, which decides from it whether to direct
 * play, direct stream or transcode. Based on jellyfin-android's builder; [includeVideo] is off for
 * the audio-only apps. [decoders] is read once, on first use.
 */
class DeviceProfileBuilder(
    private val name: String,
    private val includeVideo: Boolean,
    decoders: () -> DeviceDecoders = DeviceDecoders::fromDevice,
) {
    private val decoders by lazy(decoders)

    fun getDeviceProfile(): DeviceProfile {
        val supportedVideo = AVAILABLE_VIDEO_CODECS.map { codecs ->
            if (includeVideo) codecs.filter { it in decoders.video } else emptyList()
        }
        val supportedAudio = AVAILABLE_AUDIO_CODECS.map { codecs ->
            codecs.filter { it in decoders.audio || it in FORCED_AUDIO_CODECS }
        }

        val containerProfiles = ArrayList<ContainerProfile>()
        val directPlayProfiles = ArrayList<DirectPlayProfile>()
        val codecProfiles = ArrayList<CodecProfile>()
        SUPPORTED_CONTAINER_FORMATS.forEachIndexed { i, container ->
            if (supportedVideo[i].isNotEmpty()) {
                containerProfiles += ContainerProfile(type = DlnaProfileType.VIDEO, container = container, conditions = emptyList())
                directPlayProfiles += DirectPlayProfile(
                    type = DlnaProfileType.VIDEO,
                    container = container,
                    videoCodec = supportedVideo[i].joinToString(","),
                    audioCodec = supportedAudio[i].joinToString(","),
                )
                supportedVideo[i].mapNotNullTo(codecProfiles) { videoCodec -> codecProfile(container, videoCodec) }
            }
            if (supportedAudio[i].isNotEmpty()) {
                containerProfiles += ContainerProfile(type = DlnaProfileType.AUDIO, container = container, conditions = emptyList())
                directPlayProfiles += DirectPlayProfile(
                    type = DlnaProfileType.AUDIO,
                    container = container,
                    audioCodec = supportedAudio[i].joinToString(","),
                )
            }
        }

        return DeviceProfile(
            name = name,
            directPlayProfiles = directPlayProfiles,
            transcodingProfiles = if (includeVideo) VIDEO_TRANSCODING_PROFILES + AUDIO_TRANSCODING_PROFILES else AUDIO_TRANSCODING_PROFILES,
            containerProfiles = containerProfiles,
            codecProfiles = codecProfiles,
            subtitleProfiles = subtitleProfiles(EXO_EMBEDDED_SUBTITLES, EXO_EXTERNAL_SUBTITLES),
            maxStreamingBitrate = MAX_STREAMING_BITRATE,
            maxStaticBitrate = MAX_STATIC_BITRATE,
            musicStreamingTranscodingBitrate = MAX_MUSIC_TRANSCODING_BITRATE,
        )
    }

    /** Limits direct play of [videoCodec] to the profiles the device's decoders handle. */
    private fun codecProfile(container: String, videoCodec: String): CodecProfile? {
        val profiles = decoders.video[videoCodec]
        if (profiles.isNullOrEmpty()) return null
        return CodecProfile(
            type = CodecType.VIDEO,
            container = container,
            codec = videoCodec,
            applyConditions = emptyList(),
            conditions = listOf(
                ProfileCondition(
                    condition = ProfileConditionType.EQUALS_ANY,
                    property = ProfileConditionValue.VIDEO_PROFILE,
                    value = profiles.joinToString("|"),
                    isRequired = false,
                ),
            ),
        )
    }

    private fun subtitleProfiles(embedded: List<String>, external: List<String>): List<SubtitleProfile> =
        embedded.map { SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EMBED) } +
            external.map { SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EXTERNAL) }

    private companion object {
        /**
         * Container formats ExoPlayer plays. IMPORTANT: [AVAILABLE_VIDEO_CODECS] and
         * [AVAILABLE_AUDIO_CODECS] map codecs to the container at the same index.
         */
        val SUPPORTED_CONTAINER_FORMATS = listOf(
            "mp4", "fmp4", "webm", "mkv", "mp3", "ogg", "wav", "mpegts", "flv", "aac", "flac", "3gp",
        )

        val AVAILABLE_VIDEO_CODECS = listOf(
            // mp4
            listOf("mpeg1video", "mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1", "vp9"),
            // fmp4
            listOf("mpeg1video", "mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1", "vp9"),
            // webm
            listOf("vp8", "vp9", "av1"),
            // mkv
            listOf("mpeg1video", "mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1", "vp8", "vp9"),
            // mp3
            emptyList(),
            // ogg
            emptyList(),
            // wav
            emptyList(),
            // mpegts
            listOf("mpeg1video", "mpeg2video", "mpeg4", "h264", "hevc"),
            // flv
            listOf("mpeg4", "h264"),
            // aac
            emptyList(),
            // flac
            emptyList(),
            // 3gp
            listOf("h263", "mpeg4", "h264", "hevc"),
        )

        /** PCM codecs ExoPlayer plays by default. */
        val PCM_CODECS = listOf(
            "pcm_s8",
            "pcm_s16be",
            "pcm_s16le",
            "pcm_s24le",
            "pcm_s32le",
            "pcm_f32le",
            "pcm_alaw",
            "pcm_mulaw",
        )

        val AVAILABLE_AUDIO_CODECS = listOf(
            // mp4
            listOf("mp1", "mp2", "mp3", "aac", "alac", "ac3", "opus"),
            // fmp4
            listOf("mp3", "aac", "ac3", "eac3"),
            // webm
            listOf("vorbis", "opus"),
            // mkv
            PCM_CODECS + listOf("mp1", "mp2", "mp3", "aac", "vorbis", "opus", "flac", "alac", "ac3", "eac3", "dts", "mlp", "truehd"),
            // mp3
            listOf("mp3"),
            // ogg
            listOf("vorbis", "opus", "flac"),
            // wav
            PCM_CODECS,
            // mpegts
            PCM_CODECS + listOf("mp1", "mp2", "mp3", "aac", "ac3", "eac3", "dts", "mlp", "truehd"),
            // flv
            listOf("mp3", "aac"),
            // aac
            listOf("aac"),
            // flac
            listOf("flac"),
            // 3gp
            listOf("3gpp", "aac", "flac"),
        )

        /**
         * Audio codecs offered whether or not [MediaCodecList] lists them: ExoPlayer decodes these
         * itself or through an extension.
         */
        val FORCED_AUDIO_CODECS = PCM_CODECS + listOf("alac", "aac", "ac3", "eac3", "dts", "mlp", "truehd")

        val VIDEO_TRANSCODING_PROFILES = listOf(
            TranscodingProfile(
                type = DlnaProfileType.VIDEO,
                container = "ts",
                videoCodec = "h264",
                audioCodec = "mp1,mp2,mp3,aac,ac3,eac3,dts,mlp,truehd",
                protocol = MediaStreamProtocol.HLS,
                conditions = emptyList(),
            ),
            TranscodingProfile(
                type = DlnaProfileType.VIDEO,
                container = "mkv",
                videoCodec = "h264",
                audioCodec = AVAILABLE_AUDIO_CODECS[SUPPORTED_CONTAINER_FORMATS.indexOf("mkv")].joinToString(","),
                protocol = MediaStreamProtocol.HLS,
                conditions = emptyList(),
            ),
        )

        val AUDIO_TRANSCODING_PROFILES = listOf(
            TranscodingProfile(
                type = DlnaProfileType.AUDIO,
                container = "mp3",
                videoCodec = "",
                audioCodec = "mp3",
                protocol = MediaStreamProtocol.HTTP,
                conditions = emptyList(),
            ),
        )

        val EXO_EMBEDDED_SUBTITLES = listOf("dvbsub", "pgssub", "srt", "subrip", "ttml")
        val EXO_EXTERNAL_SUBTITLES = listOf("srt", "subrip", "ttml", "vtt", "webvtt")

        /** From Jellyfin Web's browserDeviceProfile.js. */
        const val MAX_STREAMING_BITRATE = 120_000_000
        const val MAX_STATIC_BITRATE = 100_000_000
        const val MAX_MUSIC_TRANSCODING_BITRATE = 384_000
    }
}

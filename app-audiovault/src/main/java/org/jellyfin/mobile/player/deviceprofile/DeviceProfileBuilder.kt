package org.jellyfin.mobile.player.deviceprofile

import android.media.MediaCodecList
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.model.api.ContainerProfile
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodingProfile

class DeviceProfileBuilder {
    private val supportedAudioCodecs: Array<Array<String>>

    private val transcodingProfiles: List<TranscodingProfile>

    init {
        require(
            SUPPORTED_CONTAINER_FORMATS.size == AVAILABLE_AUDIO_CODECS.size,
        )

        // Load Android-supported audio codecs
        val audioCodecs: MutableMap<String, DeviceCodec.Audio> = HashMap()
        val androidCodecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (codecInfo in androidCodecs.codecInfos) {
            if (codecInfo.isEncoder) continue

            for (mimeType in codecInfo.supportedTypes) {
                val codec = DeviceCodec.from(codecInfo.getCapabilitiesForType(mimeType)) ?: continue
                if (codec is DeviceCodec.Audio) {
                    val name = codec.name
                    if (audioCodecs.containsKey(mimeType)) {
                        audioCodecs[name] = audioCodecs[name]!!.mergeCodec(codec)
                    } else {
                        audioCodecs[name] = codec
                    }
                }
            }
        }

        // Build map of supported audio codecs from device support and hardcoded data
        supportedAudioCodecs = Array(AVAILABLE_AUDIO_CODECS.size) { i ->
            AVAILABLE_AUDIO_CODECS[i].filter { codec ->
                audioCodecs.containsKey(codec) || codec in FORCED_AUDIO_CODECS
            }.toTypedArray()
        }

        transcodingProfiles = listOf(
            TranscodingProfile(
                type = DlnaProfileType.AUDIO,
                container = "mp3",
                videoCodec = "",
                audioCodec = "mp3",
                protocol = MediaStreamProtocol.HTTP,
                conditions = emptyList(),
            ),
        )
    }

    fun getDeviceProfile(): DeviceProfile {
        val containerProfiles = ArrayList<ContainerProfile>()
        val directPlayProfiles = ArrayList<DirectPlayProfile>()

        for (i in SUPPORTED_CONTAINER_FORMATS.indices) {
            if (supportedAudioCodecs[i].isNotEmpty()) {
                containerProfiles.add(
                    ContainerProfile(
                        type = DlnaProfileType.AUDIO,
                        container = SUPPORTED_CONTAINER_FORMATS[i],
                        conditions = emptyList(),
                    ),
                )
                directPlayProfiles.add(
                    DirectPlayProfile(
                        type = DlnaProfileType.AUDIO,
                        container = SUPPORTED_CONTAINER_FORMATS[i],
                        audioCodec = supportedAudioCodecs[i].joinToString(","),
                    ),
                )
            }
        }

        // The stripped-down audiobook app does not expose an "ASS direct-play" preference.
        // Keep the default subtitle profile set.
        val subtitleProfiles = getSubtitleProfiles(EXO_EMBEDDED_SUBTITLES, EXO_EXTERNAL_SUBTITLES)

        return DeviceProfile(
            name = Constants.APP_INFO_NAME,
            directPlayProfiles = directPlayProfiles,
            transcodingProfiles = transcodingProfiles,
            containerProfiles = containerProfiles,
            codecProfiles = emptyList(),
            subtitleProfiles = subtitleProfiles,
            maxStreamingBitrate = MAX_STREAMING_BITRATE,
            maxStaticBitrate = MAX_STATIC_BITRATE,
            musicStreamingTranscodingBitrate = MAX_MUSIC_TRANSCODING_BITRATE,
        )
    }

    private fun getSubtitleProfiles(embedded: Array<String>, external: Array<String>): List<SubtitleProfile> = ArrayList<SubtitleProfile>().apply {
        for (format in embedded) {
            add(SubtitleProfile(format = format, method = SubtitleDeliveryMethod.EMBED))
        }
        for (format in external) {
            add(SubtitleProfile(format = format, method = SubtitleDeliveryMethod.EXTERNAL))
        }
    }

    companion object {

        /**
         * List of container formats supported by ExoPlayer for audio
         *
         * IMPORTANT: Don't change without updating [AVAILABLE_AUDIO_CODECS]
         */
        private val SUPPORTED_CONTAINER_FORMATS = arrayOf(
            "mp4", "fmp4", "webm", "mkv", "mp3", "ogg", "wav", "mpegts", "flv", "aac", "flac", "3gp",
        )

        /**
         * List of PCM codecs supported by ExoPlayer by default
         */
        private val PCM_CODECS = arrayOf(
            "pcm_s8",
            "pcm_s16be",
            "pcm_s16le",
            "pcm_s24le",
            "pcm_s32le",
            "pcm_f32le",
            "pcm_alaw",
            "pcm_mulaw",
        )

        /**
         * IMPORTANT: Must have same length as [SUPPORTED_CONTAINER_FORMATS],
         * as it maps the codecs to the containers with the same index!
         */
        private val AVAILABLE_AUDIO_CODECS = arrayOf(
            // mp4
            arrayOf("mp1", "mp2", "mp3", "aac", "alac", "ac3", "opus"),
            // fmp4
            arrayOf("mp3", "aac", "ac3", "eac3"),
            // webm
            arrayOf("vorbis", "opus"),
            // mkv
            arrayOf(*PCM_CODECS, "mp1", "mp2", "mp3", "aac", "vorbis", "opus", "flac", "alac", "ac3", "eac3", "dts", "mlp", "truehd"),
            // mp3
            arrayOf("mp3"),
            // ogg
            arrayOf("vorbis", "opus", "flac"),
            // wav
            PCM_CODECS,
            // mpegts
            arrayOf(*PCM_CODECS, "mp1", "mp2", "mp3", "aac", "ac3", "eac3", "dts", "mlp", "truehd"),
            // flv
            arrayOf("mp3", "aac"),
            // aac
            arrayOf("aac"),
            // flac
            arrayOf("flac"),
            // 3gp
            arrayOf("3gpp", "aac", "flac"),
        )

        /**
         * List of audio codecs that will be added to the device profile regardless of [MediaCodecList] advertising them.
         * This is especially useful for codecs supported by decoders integrated to ExoPlayer or added through an extension.
         */
        private val FORCED_AUDIO_CODECS = arrayOf(*PCM_CODECS, "alac", "aac", "ac3", "eac3", "dts", "mlp", "truehd")

        private val EXO_EMBEDDED_SUBTITLES = arrayOf("dvbsub", "pgssub", "srt", "subrip", "ttml")
        private val EXO_EXTERNAL_SUBTITLES = arrayOf("srt", "subrip", "ttml", "vtt", "webvtt")

        /**
         * Taken from Jellyfin Web:
         * https://github.com/jellyfin/jellyfin-web/blob/de690740f03c0568ba3061c4c586bd78b375d882/src/scripts/browserDeviceProfile.js#L276
         */
        private const val MAX_STREAMING_BITRATE = 120000000

        /**
         * Taken from Jellyfin Web:
         * https://github.com/jellyfin/jellyfin-web/blob/de690740f03c0568ba3061c4c586bd78b375d882/src/scripts/browserDeviceProfile.js#L372
         */
        private const val MAX_STATIC_BITRATE = 100000000

        /**
         * Taken from Jellyfin Web:
         * https://github.com/jellyfin/jellyfin-web/blob/de690740f03c0568ba3061c4c586bd78b375d882/src/scripts/browserDeviceProfile.js#L373
         */
        private const val MAX_MUSIC_TRANSCODING_BITRATE = 384000
    }
}

package org.jellyfin.mobile.cast

/**
 * How a cast device gets a file in a format it plays (Google's supported media list): the [asked]
 * containers come as they are, each only with the codecs named (`container|codec`) where it holds
 * others too; anything else is transcoded to [transcodeTo] in [transcodeCodec]. Either way it's
 * served as [mimeType], so the type the cast device is told is the one it gets. [maxSampleRate] and
 * [maxBitDepth] bound what comes, where a format can carry more than a cast device decodes. An app
 * asks the server's universal audio stream for these (see [castFormatOf]).
 */
data class CastFormat(
    val asked: List<String>,
    val transcodeTo: String,
    val transcodeCodec: String,
    val mimeType: String,
    val maxSampleRate: Int? = null,
    val maxBitDepth: Int? = null,
) {
    /**
     * Whether a file in [container] holding [audioCodec] is one of those [asked] for, so the server
     * sends it as it is; a codec not known counts as one asked for (the server then transcodes it,
     * from its start, if it isn't).
     */
    fun playsAsIs(container: String?, audioCodec: String?): Boolean {
        val name = container?.lowercase() ?: return false
        return asked.any { entry ->
            val (askedContainer, askedCodec) = entry.split('|').let { it[0] to it.getOrNull(1) }
            askedContainer == name && (askedCodec == null || audioCodec == null || askedCodec.equals(audioCodec, true))
        }
    }
}

/**
 * Without limits: the server's mp3 encoder fails on them (Jellyfin passes them on, and mp3 tops out
 * at 48 kHz), and an mp3 never carries more than a cast device decodes anyway.
 */
val CAST_MP3 = CastFormat(listOf("mp3"), "mp3", "mp3", "audio/mpeg")

/** No more than a cast device decodes (Google's supported media list: FLAC up to 96 kHz, 24 bits). */
private val CAST_FLAC =
    CastFormat(listOf("flac"), "flac", "flac", "audio/flac", maxSampleRate = 96_000, maxBitDepth = 24)

/** By the file's container; lossless ones (wav too) as FLAC, which is smaller and plays alike. */
private val CAST_FORMATS = mapOf(
    "flac" to CAST_FLAC,
    "wav" to CAST_FLAC,
    "mp3" to CAST_MP3,
    "aac" to CastFormat(listOf("aac"), "aac", "aac", "audio/aac"),
    "ogg" to CastFormat(listOf("ogg|vorbis", "ogg|opus"), "ogg", "opus", "audio/ogg"),
    "opus" to CastFormat(listOf("opus|opus"), "ogg", "opus", "audio/ogg"),
    "webm" to CastFormat(listOf("webm|opus", "webm|vorbis"), "webm", "opus", "audio/webm"),
)

/**
 * The mp4 family, which holds ALAC as well as AAC: cast devices only play the AAC. Asked for with the
 * codec, the server sends an AAC file as it is (byte ranges, so a cast device can seek in it), and
 * transcodes anything else to AAC in mp4 (which it serves as video/mp4: the Default Media Receiver
 * plays that too), so the type the cast device is told holds either way.
 */
private val MP4_CONTAINERS = setOf("m4b", "m4a", "mp4")

/**
 * How a file in [container] holding [audioCodec] (null when not known) is cast; MP3 when it can't go
 * as it is. Whether a file does go as it is: [CastFormat.playsAsIs].
 */
fun castFormatOf(container: String?, audioCodec: String? = null): CastFormat {
    val name = container?.lowercase() ?: return CAST_MP3
    if (name in MP4_CONTAINERS) {
        return if (audioCodec == null || audioCodec.equals("aac", ignoreCase = true)) {
            CastFormat(listOf("$name|aac"), "mp4", "aac", "audio/mp4")
        } else {
            CAST_MP3
        }
    }
    return CAST_FORMATS[name] ?: CAST_MP3
}

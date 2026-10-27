package org.jellyfin.mobile.cast

/**
 * How a cast device gets a file in a format it plays (Google's supported media list): the [asked]
 * containers come as they are, each only with the codecs named (`container|codec`) where it holds
 * others too; anything else is transcoded to [transcodeTo] in [transcodeCodec]. Either way it's
 * served as [mimeType], so the type the cast device is told is the one it gets. [maxSampleRate] and
 * [maxBitDepth] bound what comes, where a format can carry more than a cast device decodes. An app
 * asks the server's universal audio stream for these (see [castFormatOf]).
 */
class CastFormat(
    val asked: List<String>,
    val transcodeTo: String,
    val transcodeCodec: String,
    val mimeType: String,
    val maxSampleRate: Int? = null,
    val maxBitDepth: Int? = null,
)

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
 * How a file in [container] is cast; MP3 when it can't go as it is. Not m4a or mp4: they hold ALAC
 * too, which cast devices can't play.
 */
fun castFormatOf(container: String?): CastFormat = container?.lowercase()?.let(CAST_FORMATS::get) ?: CAST_MP3

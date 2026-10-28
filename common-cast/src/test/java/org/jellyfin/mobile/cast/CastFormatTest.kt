package org.jellyfin.mobile.cast

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** What a cast device is asked to get for a file, by its container. */
class CastFormatTest {

    @Test
    fun `formats cast devices play go as they are, as their own type`() {
        castFormatOf("mp3").run {
            asked shouldBe listOf("mp3")
            mimeType shouldBe "audio/mpeg"
        }
        castFormatOf("flac").mimeType shouldBe "audio/flac"
        castFormatOf("aac").mimeType shouldBe "audio/aac"
    }

    @Test
    fun `the container is matched whatever its case`() {
        castFormatOf("FLAC").asked shouldBe listOf("flac")
    }

    @Test
    fun `lossless files come as flac, no more than a cast device decodes`() {
        castFormatOf("wav").run {
            asked shouldBe listOf("flac")
            transcodeTo shouldBe "flac"
            mimeType shouldBe "audio/flac"
            maxSampleRate shouldBe 96_000
            maxBitDepth shouldBe 24
        }
    }

    @Test
    fun `a container that holds several codecs is asked for only with those cast devices play`() {
        castFormatOf("ogg").run {
            asked shouldBe listOf("ogg|vorbis", "ogg|opus")
            transcodeTo shouldBe "ogg"
            transcodeCodec shouldBe "opus"
            mimeType shouldBe "audio/ogg"
        }
    }

    @Test
    fun `an mp4-family file goes as it is if it holds aac, which cast devices play, as an mp4`() {
        listOf("m4a", "m4b", "mp4").forEach { container ->
            castFormatOf(container, "aac").run {
                asked shouldBe listOf("$container|aac")
                transcodeTo shouldBe "mp4"
                transcodeCodec shouldBe "aac"
                mimeType shouldBe "audio/mp4"
            }
        }
    }

    @Test
    fun `an mp4-family file of no known codec is asked for as aac, which the server makes of anything else`() {
        // ALAC, say: transcoded to AAC in mp4, so the type the cast device was told still holds
        castFormatOf("m4a") shouldBe castFormatOf("m4a", "aac")
    }

    @Test
    fun `an mp4-family file known to hold something else comes as mp3`() {
        castFormatOf("m4a", "alac") shouldBe CAST_MP3
    }

    @Test
    fun `whether a file goes as it is, by its container and its codec`() {
        castFormatOf("mp3").playsAsIs(AudioFileInfo("MP3", "mp3")) shouldBe true
        castFormatOf("ogg").playsAsIs(AudioFileInfo("ogg", "vorbis")) shouldBe true
        castFormatOf("ogg").playsAsIs(AudioFileInfo("ogg", "flac")) shouldBe false
        castFormatOf("wav").playsAsIs(AudioFileInfo("wav", "pcm_s16le", 44_100, 16)) shouldBe false
        castFormatOf("m4a", "alac").playsAsIs(AudioFileInfo("m4a", "alac")) shouldBe false
        castFormatOf(null).playsAsIs(AudioFileInfo(null, null)) shouldBe false
    }

    @Test
    fun `a file that may not be one a cast device plays isn't sent as it is, since the server would transcode it from its start`() {
        // Its codec not known, where the container holds others too (ALAC in m4a, FLAC in ogg)
        castFormatOf("ogg").playsAsIs(AudioFileInfo("ogg", null)) shouldBe false
        castFormatOf("m4a").playsAsIs(AudioFileInfo("m4a", null)) shouldBe false
        // Past what a cast device decodes, or not known to be within it
        castFormatOf("flac").playsAsIs(AudioFileInfo("flac", "flac", 192_000, 24)) shouldBe false
        castFormatOf("flac").playsAsIs(AudioFileInfo("flac", "flac", 96_000, 32)) shouldBe false
        castFormatOf("flac").playsAsIs(AudioFileInfo("flac", "flac", null, 16)) shouldBe false
        castFormatOf("flac").playsAsIs(AudioFileInfo("flac", "flac", 44_100, 16)) shouldBe true
        castFormatOf("flac").playsAsIs(AudioFileInfo("flac", "flac", 96_000, 24)) shouldBe true
        // Where the format has no limits, none need be known
        castFormatOf("mp3").playsAsIs(AudioFileInfo("mp3", "mp3", null, null)) shouldBe true
    }

    @Test
    fun `a file is cast in its own format where it can, and as mp3 where it can't or isn't to be kept`() {
        castChoiceOf(AudioFileInfo("flac", "flac", 44_100, 16), keepFormat = true) shouldBe
            CastChoice(castFormatOf("flac"), asIs = true)
        castChoiceOf(AudioFileInfo("flac", "flac", 192_000, 24), keepFormat = true) shouldBe
            CastChoice(castFormatOf("flac"), asIs = false)
        castChoiceOf(AudioFileInfo("m4b", "alac"), keepFormat = true) shouldBe CastChoice(CAST_MP3, asIs = false)
        // The server couldn't say what it is
        castChoiceOf(null, keepFormat = true) shouldBe CastChoice(CAST_MP3, asIs = false)
        // A lower quality: the server transcodes it down anyway
        castChoiceOf(AudioFileInfo("flac", "flac", 44_100, 16), keepFormat = false) shouldBe
            CastChoice(CAST_MP3, asIs = false)
    }

    @Test
    fun `anything else, or a file of no known container, comes as mp3`() {
        castFormatOf("wma") shouldBe CAST_MP3
        castFormatOf(null) shouldBe CAST_MP3
        CAST_MP3.run {
            asked shouldBe listOf("mp3")
            transcodeTo shouldBe "mp3"
            mimeType shouldBe "audio/mpeg"
        }
    }

    @Test
    fun `an mp3 transcode is asked for without the lossless limits, which the server's mp3 encoder fails on`() {
        CAST_MP3.maxSampleRate shouldBe null
        CAST_MP3.maxBitDepth shouldBe null
    }
}

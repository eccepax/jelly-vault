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
    fun `whether a file goes as it is, by its container and its codec where that's known`() {
        castFormatOf("flac").playsAsIs("flac", null) shouldBe true
        castFormatOf("mp3").playsAsIs("MP3", "mp3") shouldBe true
        castFormatOf("ogg").playsAsIs("ogg", "vorbis") shouldBe true
        castFormatOf("ogg").playsAsIs("ogg", "flac") shouldBe false
        // Not known: as it is, as far as can be told (the server transcodes from the start if not)
        castFormatOf("ogg").playsAsIs("ogg", null) shouldBe true
        castFormatOf("wav").playsAsIs("wav", null) shouldBe false
        castFormatOf("m4a", "alac").playsAsIs("m4a", "alac") shouldBe false
        castFormatOf(null).playsAsIs(null, null) shouldBe false
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

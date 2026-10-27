package org.jellyfin.mobile.cast

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** What a cast device is asked to get for a file, by its container and (where known) codec. */
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
    fun `an mp4 file known to hold AAC goes as it is, asked for with that codec`() {
        listOf("m4b", "m4a", "mp4").forEach { container ->
            castFormatOf(container, audioCodec = "AAC").run {
                asked shouldBe listOf("$container|aac")
                transcodeCodec shouldBe "aac"
                mimeType shouldBe "audio/mp4"
            }
        }
    }

    @Test
    fun `an mp4 file holding anything else, or not known to hold AAC, comes as mp3`() {
        // ALAC, say, which cast devices can't play
        castFormatOf("m4b", audioCodec = "alac") shouldBe CAST_MP3
        castFormatOf("m4a") shouldBe CAST_MP3
    }

    @Test
    fun `anything else, or a file of no known container, comes as mp3`() {
        castFormatOf("m4a") shouldBe CAST_MP3
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

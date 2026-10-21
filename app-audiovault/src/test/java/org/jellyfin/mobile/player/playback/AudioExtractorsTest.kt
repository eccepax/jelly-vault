package org.jellyfin.mobile.player.playback

import android.app.Application
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.test.utils.FakeExtractorInput
import androidx.media3.test.utils.FakeExtractorOutput
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A file with no seek table (a raw AAC, an MP3 without one) can still be sought in by its bitrate,
 * as long as its length is known: a file played directly or from the device. A transcode's length
 * isn't, so it stays a stream that seeks restart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AudioExtractorsTest {

    @Test
    fun `a raw AAC file can be sought in`() {
        seekMapOf(adtsFile(), lengthKnown = true).isSeekable shouldBe true
    }

    @Test
    fun `an MP3 without a seek table can be sought in`() {
        seekMapOf(mp3File(), lengthKnown = true).isSeekable shouldBe true
    }

    @Test
    fun `a transcode, whose length isn't known, can't`() {
        seekMapOf(adtsFile(), lengthKnown = false).isSeekable shouldBe false
        seekMapOf(mp3File(), lengthKnown = false).isSeekable shouldBe false
    }

    private fun seekMapOf(file: ByteArray, lengthKnown: Boolean): SeekMap {
        fun input() = FakeExtractorInput.Builder().setData(file).setSimulateUnknownLength(!lengthKnown).build()
        val extractor = audioExtractorsFactory(isLowRamDevice = false).createExtractors()
            .first { runCatching { it.sniff(input()) }.getOrDefault(false) }
        val output = FakeExtractorOutput()
        extractor.init(output)
        val input = input()
        val position = PositionHolder()
        while (output.seekMap == null) {
            when (extractor.read(input, position)) {
                Extractor.RESULT_SEEK -> input.setPosition(position.position.toInt())
                Extractor.RESULT_END_OF_INPUT -> error("No seek map before the end of the file")
            }
        }
        return output.seekMap!!
    }

    /** AAC-LC frames, 44.1 kHz stereo, each [ADTS_FRAME_BYTES] long: a constant bitrate. */
    private fun adtsFile(): ByteArray {
        val frame = ByteArray(ADTS_FRAME_BYTES)
        val header = intArrayOf(
            0xFF,
            0xF1, // sync, MPEG-4, no CRC
            0x50, // AAC-LC, 44.1 kHz
            0x80 or (ADTS_FRAME_BYTES shr 11 and 0x3), // stereo, frame length's top bits
            ADTS_FRAME_BYTES shr 3 and 0xFF,
            (ADTS_FRAME_BYTES and 0x7) shl 5 or 0x1F,
            0xFC, // buffer fullness, one raw block
        )
        header.forEachIndexed { i, byte -> frame[i] = byte.toByte() }
        return repeated(frame, FRAMES)
    }

    /** MPEG-1 layer III frames, 128 kbps at 44.1 kHz, with no Xing or VBRI header. */
    private fun mp3File(): ByteArray {
        val frame = ByteArray(MP3_FRAME_BYTES)
        intArrayOf(0xFF, 0xFB, 0x90, 0x00).forEachIndexed { i, byte -> frame[i] = byte.toByte() }
        return repeated(frame, FRAMES)
    }

    private fun repeated(frame: ByteArray, times: Int) = ByteArray(frame.size * times) { frame[it % frame.size] }

    private companion object {
        const val FRAMES = 1_500
        const val ADTS_FRAME_BYTES = 256
        const val MP3_FRAME_BYTES = 417
    }
}

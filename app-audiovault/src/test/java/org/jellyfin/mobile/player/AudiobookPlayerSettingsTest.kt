package org.jellyfin.mobile.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test

/** The settings every audiobook player is built with; ExoPlayer has no getters for most of them. */
class AudiobookPlayerSettingsTest {

    private val attributes = slot<AudioAttributes>()
    private val builder = mockk<ExoPlayer.Builder> {
        every { setAudioAttributes(capture(attributes), any()) } returns this
        every { setHandleAudioBecomingNoisy(any()) } returns this
    }

    @Test
    fun `plays as spoken word media`() {
        builder.forAudiobooks()

        attributes.captured.contentType shouldBe C.AUDIO_CONTENT_TYPE_SPEECH
        attributes.captured.usage shouldBe C.USAGE_MEDIA
    }

    @Test
    fun `pauses for other apps, even ones that would only lower music`() {
        // Spoken word pauses rather than ducks: a navigation prompt over a sentence loses the sentence.
        builder.forAudiobooks()

        verify { builder.setAudioAttributes(any(), true) }
    }

    @Test
    fun `pauses when headphones are unplugged`() {
        builder.forAudiobooks()

        verify { builder.setHandleAudioBecomingNoisy(true) }
    }
}

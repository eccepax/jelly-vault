package org.jellyfin.mobile.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The settings every audiobook player is built with; ExoPlayer has no getters for most of them. */
class AudiobookPlayerSettingsTest {

    private var attributes: AudioAttributes? = null
    private var handlesAudioFocus: Boolean? = null
    private var handlesBecomingNoisy: Boolean? = null

    private fun apply() = applyAudiobookSettings(
        setAudioAttributes = { attributes, handleAudioFocus ->
            this.attributes = attributes
            handlesAudioFocus = handleAudioFocus
        },
        setHandleAudioBecomingNoisy = { handlesBecomingNoisy = it },
    )

    @Test
    fun `plays as spoken word media`() {
        apply()

        attributes?.contentType shouldBe C.AUDIO_CONTENT_TYPE_SPEECH
        attributes?.usage shouldBe C.USAGE_MEDIA
    }

    @Test
    fun `pauses for other apps, even ones that would only lower music`() {
        // Spoken word pauses rather than ducks: a navigation prompt over a sentence loses the sentence.
        apply()

        handlesAudioFocus shouldBe true
    }

    @Test
    fun `pauses when headphones are unplugged`() {
        apply()

        handlesBecomingNoisy shouldBe true
    }
}

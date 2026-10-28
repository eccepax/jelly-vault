package org.jellyfin.mobile.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

/** Audiobooks are spoken word, for the player and the media session alike. */
const val AUDIOBOOK_CONTENT_TYPE = C.AUDIO_CONTENT_TYPE_SPEECH

/**
 * What every audiobook player is built with: spoken-word audio that pauses for other apps (speech
 * pauses where music would only get quieter, so a navigation prompt doesn't talk over a sentence),
 * and that pauses when headphones come out. How much to keep awake depends on the book; see
 * [org.jellyfin.mobile.common.downloads.wakeModeFor].
 */
fun ExoPlayer.Builder.forAudiobooks(): ExoPlayer.Builder = apply {
    applyAudiobookSettings(
        setAudioAttributes = { attributes, handleAudioFocus -> setAudioAttributes(attributes, handleAudioFocus) },
        setHandleAudioBecomingNoisy = { setHandleAudioBecomingNoisy(it) },
    )
}

/** [forAudiobooks]'s settings, given to whatever takes them (ExoPlayer's builder can't be mocked). */
internal fun applyAudiobookSettings(
    setAudioAttributes: (AudioAttributes, handleAudioFocus: Boolean) -> Unit,
    setHandleAudioBecomingNoisy: (Boolean) -> Unit,
) {
    setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(AUDIOBOOK_CONTENT_TYPE)
            .build(),
        // handleAudioFocus: pause for calls, other apps and navigation prompts.
        true,
    )
    setHandleAudioBecomingNoisy(true)
}

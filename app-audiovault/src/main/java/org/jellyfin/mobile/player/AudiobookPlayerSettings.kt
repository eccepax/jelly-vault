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
 * [wakeModeFor].
 */
fun ExoPlayer.Builder.forAudiobooks(): ExoPlayer.Builder = this
    .setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(AUDIOBOOK_CONTENT_TYPE)
            .build(),
        // handleAudioFocus: pause for calls, other apps and navigation prompts.
        true,
    )
    .setHandleAudioBecomingNoisy(true)

/**
 * What to keep awake while a track plays with the screen off: a streamed one needs Wi-Fi too, one
 * playing from the device only the CPU (a Wi-Fi lock would just drain the battery).
 */
fun wakeModeFor(onDevice: Boolean): Int = if (onDevice) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NETWORK

package org.jellyfin.mobile.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

/**
 * What every audiobook player is built with: spoken-word audio that pauses or ducks for other apps,
 * pauses when headphones come out, and keeps Wi-Fi up while streaming with the screen off.
 */
fun ExoPlayer.Builder.forAudiobooks(): ExoPlayer.Builder = this
    .setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build(),
        // handleAudioFocus: pause for calls/other apps, duck for navigation prompts.
        true,
    )
    .setHandleAudioBecomingNoisy(true)
    .setWakeMode(C.WAKE_MODE_NETWORK)

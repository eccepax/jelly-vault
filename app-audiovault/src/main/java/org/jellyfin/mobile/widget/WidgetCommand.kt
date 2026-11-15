package org.jellyfin.mobile.widget

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.player.playback.AudiobookPlaybackService
import kotlin.coroutines.resume

/** What the widget's buttons do to the playback service's player. */
enum class WidgetCommand {
    /** Pauses a book playing; plays one paused, loading it again if it had stopped. */
    PLAY_PAUSE,

    /** Back 10 seconds, as the player's own button. */
    REWIND,
    ;

    fun perform(player: Player) {
        when (this) {
            PLAY_PAUSE -> if (player.playWhenReady) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
            REWIND -> player.seekBack()
        }
    }
}

/** Sends [command] to the playback service, through a controller made for it and let go after. */
internal suspend fun sendToPlayback(context: Context, command: WidgetCommand) {
    val token = SessionToken(context, ComponentName(context, AudiobookPlaybackService::class.java))
    val connecting = MediaController.Builder(context, token).buildAsync()
    val controller = suspendCancellableCoroutine { waiting ->
        connecting.addListener({ waiting.resume(connecting.get()) }, ContextCompat.getMainExecutor(context))
        waiting.invokeOnCancellation { connecting.cancel(true) }
    }
    withContext(Dispatchers.Main) {
        command.perform(controller)
        controller.release()
    }
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        sendToPlayback(context, WidgetCommand.PLAY_PAUSE)
}

class RewindAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        sendToPlayback(context, WidgetCommand.REWIND)
}

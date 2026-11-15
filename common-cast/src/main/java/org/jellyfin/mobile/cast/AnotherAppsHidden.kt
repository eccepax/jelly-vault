package org.jellyfin.mobile.cast

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.IdentityHashMap

/**
 * The session's cast player, showing nothing of another app's cast (see [sentFromAnotherApp]):
 * every app on a phone casts to a device in the one session, so without this each app's
 * notification and lock screen would show, and control, the other's book or song. While another
 * app's item plays it reads as nothing loaded: no items, idle, not playing, no playback commands;
 * and its listeners (the session among them) hear it so, as the session takes some of what it
 * shows from what it hears rather than asking.
 */
@OptIn(UnstableApi::class)
class AnotherAppsHidden(player: Player) : ForwardingPlayer(player) {

    private val hiding: Boolean get() = super.getCurrentMediaItem()?.sentFromAnotherApp == true

    override fun getCurrentMediaItem(): MediaItem? = if (hiding) null else super.getCurrentMediaItem()
    override fun getMediaItemCount(): Int = if (hiding) 0 else super.getMediaItemCount()
    override fun getCurrentTimeline(): Timeline = if (hiding) Timeline.EMPTY else super.getCurrentTimeline()
    override fun getMediaMetadata(): MediaMetadata = if (hiding) MediaMetadata.EMPTY else super.getMediaMetadata()
    override fun getPlaybackState(): Int = if (hiding) Player.STATE_IDLE else super.getPlaybackState()
    override fun getPlayWhenReady(): Boolean = !hiding && super.getPlayWhenReady()
    override fun isPlaying(): Boolean = !hiding && super.isPlaying()
    override fun isLoading(): Boolean = !hiding && super.isLoading()
    override fun getDuration(): Long = if (hiding) C.TIME_UNSET else super.getDuration()
    override fun getCurrentPosition(): Long = if (hiding) 0L else super.getCurrentPosition()

    override fun getAvailableCommands(): Player.Commands {
        val commands = super.getAvailableCommands()
        return if (hiding) commands.buildUpon().removeAll(*PLAYBACK_COMMANDS).build() else commands
    }

    override fun isCommandAvailable(command: Int): Boolean =
        !(hiding && command in PLAYBACK_COMMANDS) && super.isCommandAvailable(command)

    /** Each listener's stand-in, the same one taken off as was added. */
    private val standIns = IdentityHashMap<Player.Listener, Player.Listener>()

    override fun addListener(listener: Player.Listener) =
        super.addListener(synchronized(standIns) { standIns.getOrPut(listener) { hearingOwnOnly(listener) } })

    override fun removeListener(listener: Player.Listener) =
        super.removeListener(synchronized(standIns) { standIns.remove(listener) } ?: listener)

    /**
     * [listener], told while another app's item plays that nothing's loaded and playing, and told
     * all else as it is. A proxy, since a listener's callbacks are default methods, which Kotlin's
     * delegation doesn't pass on.
     */
    private fun hearingOwnOnly(listener: Player.Listener): Player.Listener =
        Proxy.newProxyInstance(
            Player.Listener::class.java.classLoader,
            arrayOf(Player.Listener::class.java),
            OwnOnly(listener),
        ) as Player.Listener

    private inner class OwnOnly(val listener: Player.Listener) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? = when (method.name) {
            "equals" -> proxy === args?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "OwnOnly($listener)"
            else -> {
                val sent: Array<Any?> = args?.let { arrayOf(*it) } ?: emptyArray()
                if (hiding) hide(method.name, sent)
                try {
                    method.invoke(listener, *sent)
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }
            }
        }

        private fun hide(name: String, args: Array<Any?>) {
            when (name) {
                "onTimelineChanged" -> args[0] = Timeline.EMPTY
                "onMediaItemTransition" -> args[0] = null
                "onMediaMetadataChanged" -> args[0] = MediaMetadata.EMPTY
                "onPlaybackStateChanged" -> args[0] = Player.STATE_IDLE
                "onIsPlayingChanged", "onPlayWhenReadyChanged", "onIsLoadingChanged" -> args[0] = false
                "onAvailableCommandsChanged" -> args[0] = availableCommands
            }
        }
    }

    private companion object {
        /** What can't be done to another app's cast from here: play it, move about in it, change it. */
        val PLAYBACK_COMMANDS = intArrayOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SET_SPEED_AND_PITCH,
        )
    }
}

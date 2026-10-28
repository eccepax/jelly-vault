package org.jellyfin.mobile.player.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.IdentityHashMap

/**
 * A [ForwardingPlayer] that offers commands of its own (see [getAvailableCommands]), and says so to
 * its listeners: ForwardingPlayer passes on the player's own commands as they change, and the
 * session sends what it hears, as it is, to the app's MediaController and the lock screen, which
 * only send commands they were told are available.
 */
abstract class OwnCommandsPlayer(player: Player) : ForwardingPlayer(player) {

    /**
     * Each listener's proxy, the same one taken off as was added: ForwardingPlayer keeps its listeners
     * by identity (since Media3 1.11), so an equal proxy made again wouldn't be found.
     */
    private val proxies = IdentityHashMap<Player.Listener, Player.Listener>()

    override fun addListener(listener: Player.Listener) =
        super.addListener(synchronized(proxies) { proxies.getOrPut(listener) { hearingOwnCommands(listener) } })

    override fun removeListener(listener: Player.Listener) =
        super.removeListener(synchronized(proxies) { proxies.remove(listener) } ?: listener)

    /**
     * [listener], told of this player's commands rather than the player's own, and told everything
     * else as it is. A proxy, since every one of a listener's callbacks is a default method, which
     * Kotlin's delegation doesn't pass on (nor would a list of them written out keep up with Media3's).
     * Equal for the same listener, so it can be taken off again.
     */
    private fun hearingOwnCommands(listener: Player.Listener): Player.Listener =
        Proxy.newProxyInstance(
            Player.Listener::class.java.classLoader,
            arrayOf(Player.Listener::class.java),
            OwnCommands(listener),
        ) as Player.Listener

    private inner class OwnCommands(val listener: Player.Listener) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? = when (method.name) {
            "equals" -> args?.get(0)?.let(::handlerOf)?.let { it.listener == listener && it.player() === player() } == true
            "hashCode" -> listener.hashCode()
            "toString" -> "OwnCommands($listener)"
            "onAvailableCommandsChanged" -> listener.onAvailableCommandsChanged(availableCommands)
            else -> try {
                method.invoke(listener, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }

        fun player(): OwnCommandsPlayer = this@OwnCommandsPlayer

        private fun handlerOf(other: Any): OwnCommands? =
            if (Proxy.isProxyClass(other.javaClass)) Proxy.getInvocationHandler(other) as? OwnCommands else null
    }
}

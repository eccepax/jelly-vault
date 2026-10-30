package org.jellyfin.mobile.cast

import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/** Seeks while the cast device's queue is between loads. */
class CastSeekGuardTest {

    private val seeks = mutableListOf<String>()
    private var remote = true
    private var castQueueEmpty = true

    /** Hears every seek, by name; plays here or there as [remote] says. */
    private val player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
    ) { _, method, _ ->
        when {
            method.name == "getDeviceInfo" ->
                DeviceInfo.Builder(
                    if (remote) DeviceInfo.PLAYBACK_TYPE_REMOTE else DeviceInfo.PLAYBACK_TYPE_LOCAL,
                ).build()
            method.name.startsWith("seek") -> {
                seeks += method.name
                null
            }
            else -> error("not used: ${method.name}")
        }
    } as Player

    private val guard = CastSeekGuard(player) { castQueueEmpty }

    private fun seekEveryWay(on: Player) {
        on.seekTo(1, 0L)
        on.seekTo(5_000L)
        on.seekToDefaultPosition(1)
        on.seekToDefaultPosition()
        on.seekToNext()
        on.seekToNextMediaItem()
        on.seekToPrevious()
        on.seekToPreviousMediaItem()
        on.seekBack()
        on.seekForward()
    }

    @Test
    fun `a seek while the cast device's queue is empty goes nowhere`() {
        // Media3's cast player looks the song up in that queue, and would throw: next spammed while
        // the queue reloads
        seekEveryWay(guard)

        seeks shouldBe emptyList()
    }

    @Test
    fun `once the cast device's queue is back, seeks go through`() {
        castQueueEmpty = false

        seekEveryWay(guard)

        seeks.size shouldBe 10
    }

    @Test
    fun `playing here, seeks go through whatever the cast device's queue is`() {
        remote = false

        seekEveryWay(guard)

        seeks.size shouldBe 10
    }
}

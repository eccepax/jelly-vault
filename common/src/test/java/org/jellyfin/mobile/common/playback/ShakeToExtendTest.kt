package org.jellyfin.mobile.common.playback

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** [ShakeToExtend]: while the sleep timer counts down, shaking the phone gives it 15 more minutes. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShakeToExtendTest {

    private val sensor = FakeShakeSensor()
    private var enabled = true
    private var buzzes = 0

    private fun TestScope.timer(): SleepTimer {
        val timer = SleepTimer(StillTarget, backgroundScope)
        ShakeToExtend(timer, sensor, enabled = { enabled }, buzz = { buzzes++ }).start(backgroundScope)
        runCurrent()
        return timer
    }

    @Test
    fun `the phone is only felt while the timer counts down`() = runTest {
        val timer = timer()
        sensor.listening shouldBe false

        timer.start(MINUTE)
        runCurrent()
        sensor.listening shouldBe true

        timer.cancel()
        runCurrent()
        sensor.listening shouldBe false
    }

    @Test
    fun `a shake while counting down adds 15 minutes, with a buzz to say so`() = runTest {
        val timer = timer()
        timer.start(MINUTE)
        runCurrent()

        sensor.shake()

        timer.state.value shouldBe SleepTimerState.Counting(MINUTE + SleepTimer.EXTEND_MS)
        buzzes shouldBe 1
    }

    @Test
    fun `turned off in settings, the phone isn't felt at all`() = runTest {
        enabled = false
        val timer = timer()

        timer.start(MINUTE)
        runCurrent()

        sensor.listening shouldBe false
    }

    @Test
    fun `stopping at the end of a chapter isn't extended by shaking`() = runTest {
        val timer = timer()

        timer.stopAtEndOfSegment()
        runCurrent()

        sensor.listening shouldBe false
    }

    private class FakeShakeSensor : ShakeSensor {
        private var onShake: (() -> Unit)? = null
        val listening get() = onShake != null

        override fun start(onShake: () -> Unit) {
            this.onShake = onShake
        }

        override fun stop() {
            onShake = null
        }

        fun shake() = onShake?.invoke()
    }

    private object StillTarget : SleepTimerTarget {
        override fun sample() = PlaybackSample(
            segmentId = "a",
            positionMs = 0L,
            durationMs = 0L,
            isPlaying = true,
            hasNext = false,
        )
        override fun pause() = Unit
        override fun rewindToSegmentStart() = Unit
        override fun setFade(fade: Float) = Unit
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}

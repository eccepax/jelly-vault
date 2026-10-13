package org.jellyfin.mobile.music.playback

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.music.app.FakePlaybackPreferences
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerVisibilityTest {

    private val preferences = FakePlaybackPreferences()

    @Test
    fun `shown unless hidden before`() = runTest {
        SleepTimerVisibility(preferences, SleepTimer(FakePlaybackController(), backgroundScope)).isShown.value shouldBe true

        preferences.showSleepTimer = false

        SleepTimerVisibility(preferences, SleepTimer(FakePlaybackController(), backgroundScope)).isShown.value shouldBe false
    }

    @Test
    fun `hiding it is remembered, and turns off a running timer so nothing unseen pauses the music`() = runTest {
        val timer = SleepTimer(FakePlaybackController(), backgroundScope)
        val visibility = SleepTimerVisibility(preferences, timer)
        timer.start(15 * 60_000L)
        runCurrent()

        visibility.setShown(false)

        preferences.showSleepTimer shouldBe false
        visibility.isShown.value shouldBe false
        timer.state.value shouldBe SleepTimerState.Off
    }

    @Test
    fun `showing it again starts nothing`() = runTest {
        val timer = SleepTimer(FakePlaybackController(), backgroundScope)
        val visibility = SleepTimerVisibility(preferences, timer)
        visibility.setShown(false)

        visibility.setShown(true)

        visibility.isShown.value shouldBe true
        timer.state.value shouldBe SleepTimerState.Off
    }
}

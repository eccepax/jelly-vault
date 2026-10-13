package org.jellyfin.mobile.music.offline

import androidx.lifecycle.ViewModel
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReloadOnModeChangeTest {

    private class Screen : ViewModel() {
        var loads = 0
        fun reload() {
            loads++
        }
    }

    private val isOffline = MutableStateFlow(false)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `nothing reloads just for being created`() {
        val screen = Screen().reloadingOnModeChange(isOffline, Screen::reload)

        screen.loads shouldBe 0
    }

    @Test
    fun `going offline and coming back each reload`() {
        val screen = Screen().reloadingOnModeChange(isOffline, Screen::reload)

        isOffline.value = true
        screen.loads shouldBe 1

        isOffline.value = false
        screen.loads shouldBe 2
    }

    @Test
    fun `a screen created while offline reloads when the vault is back`() {
        isOffline.value = true
        val screen = Screen().reloadingOnModeChange(isOffline, Screen::reload)

        isOffline.value = false

        screen.loads shouldBe 1
    }
}

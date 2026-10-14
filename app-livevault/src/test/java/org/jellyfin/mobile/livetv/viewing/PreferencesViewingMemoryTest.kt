package org.jellyfin.mobile.livetv.viewing

import android.app.Application
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PreferencesViewingMemoryTest {

    private val context: Application get() = RuntimeEnvironment.getApplication()

    @Test
    fun `remembers nothing at first`() {
        PreferencesViewingMemory(context).apply {
            lastChannelId.value shouldBe null
            tag shouldBe null
            favoritesOnly shouldBe false
        }
    }

    @Test
    fun `remembers the channel and filter across app restarts`() {
        PreferencesViewingMemory(context).apply {
            rememberChannel("id-43")
            rememberFilter(tag = "Sports", favoritesOnly = false)
        }

        PreferencesViewingMemory(context).apply {
            lastChannelId.value shouldBe "id-43"
            tag shouldBe "Sports"
            favoritesOnly shouldBe false
        }
    }

    @Test
    fun `remembers favorites being shown`() {
        PreferencesViewingMemory(context).rememberFilter(tag = null, favoritesOnly = true)

        PreferencesViewingMemory(context).favoritesOnly shouldBe true
    }

    @Test
    fun `the last channel is published as it changes`() {
        val memory = PreferencesViewingMemory(context)

        memory.rememberChannel("id-1")

        memory.lastChannelId.value shouldBe "id-1"
    }

    @Test
    fun `forgetting clears everything`() {
        PreferencesViewingMemory(context).apply {
            rememberChannel("id-43")
            rememberFilter(tag = "Sports", favoritesOnly = false)
            forget()
            lastChannelId.value shouldBe null
        }

        PreferencesViewingMemory(context).apply {
            lastChannelId.value shouldBe null
            tag shouldBe null
        }
    }
}

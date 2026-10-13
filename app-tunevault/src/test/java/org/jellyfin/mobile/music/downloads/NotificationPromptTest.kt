package org.jellyfin.mobile.music.downloads

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Download progress shows in a notification, which Android 13+ only allows once permitted. */
class NotificationPromptTest {

    @Test
    fun `asks once, on android 13 and later, if not yet allowed`() {
        shouldAskForNotifications(sdkInt = 33, granted = false, alreadyAsked = false) shouldBe true
    }

    @Test
    fun `never asks twice, so downloading isn't nagging`() {
        shouldAskForNotifications(sdkInt = 34, granted = false, alreadyAsked = true) shouldBe false
    }

    @Test
    fun `doesn't ask when already allowed`() {
        shouldAskForNotifications(sdkInt = 34, granted = true, alreadyAsked = false) shouldBe false
    }

    @Test
    fun `before android 13 there is nothing to ask`() {
        shouldAskForNotifications(sdkInt = 32, granted = false, alreadyAsked = false) shouldBe false
    }
}

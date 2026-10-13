package org.jellyfin.mobile.player.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Regression coverage for [PlayerFragment.formatTime]. Plain JUnit5 — no Robolectric needed since
 * the method touches no injected/Android state (the Fragment's `by inject()`/`by activityViewModel()`
 * properties are all lazy and are never resolved by simply constructing the Fragment).
 */
class PlayerFragmentFormatTimeTest {

    private val fragment = PlayerFragment()

    @Test
    fun `zero is formatted as 0-00`() {
        fragment.formatTime(0L) shouldBe "0:00"
    }

    @Test
    fun `under a minute omits the hours segment`() {
        fragment.formatTime(45_000L) shouldBe "0:45"
    }

    @Test
    fun `exactly one hour includes the hours segment`() {
        fragment.formatTime(3_600_000L) shouldBe "1:00:00"
    }

    @Test
    fun `hours minutes and seconds all render together`() {
        fragment.formatTime(3_723_000L) shouldBe "1:02:03"
    }

    @Test
    fun `multi-hour durations are not truncated`() {
        fragment.formatTime(36_000_000L) shouldBe "10:00:00"
    }

    @Test
    fun `sub-second remainders are truncated, not rounded`() {
        fragment.formatTime(59_999L) shouldBe "0:59"
    }
}

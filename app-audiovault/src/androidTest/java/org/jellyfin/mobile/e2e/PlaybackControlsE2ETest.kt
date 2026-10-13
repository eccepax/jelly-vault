package org.jellyfin.mobile.e2e

import android.view.View
import android.widget.TextView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import org.hamcrest.Matcher
import org.jellyfin.mobile.R
import org.junit.Test

/**
 * Playback control buttons on `PlayerFragment`, exercised against real audio actually playing
 * from the Vault (see [BaseE2ETest] / [LoggedInE2ETest.startPlaybackFromFirstLibraryItem]).
 */
class PlaybackControlsE2ETest : LoggedInE2ETest() {

    @Test
    fun pauseThenResumeKeepsPlaybackOnScreen() {
        loginAndWaitForLibrary()
        startPlaybackFromFirstLibraryItem()

        onView(playerControl(R.id.exo_play_pause)).perform(click()) // pause
        onView(playerControl(R.id.exo_play_pause)).perform(click()) // resume

        onView(playerControl(R.id.exo_play_pause)).check(matches(isDisplayed()))
    }

    @Test
    fun fastForwardAdvancesThePlaybackPosition() {
        loginAndWaitForLibrary()
        startPlaybackFromFirstLibraryItem()

        val positionBefore = textOf(waitForPlayerControl(R.id.exo_position))
        onView(waitForPlayerControl(R.id.next_button)).perform(click()) // +30s (audiobook skip-forward interval)
        val positionAfter = textOf(waitForPlayerControl(R.id.exo_position))

        assert(positionAfter != positionBefore) {
            "Expected the displayed position to change after fast-forwarding, but it stayed \"$positionBefore\""
        }
    }

    @Test
    fun chapterSkipButtonsDoNotBreakPlayback() {
        loginAndWaitForLibrary()
        startPlaybackFromFirstLibraryItem()

        // Real Vault content may or may not have chapter markers — with none, these fall back to
        // skipping to the next/previous queue track instead (AudiobookPlayerViewModel.nextChapter/
        // previousChapter). Either way playback should still be running afterward, not crashed.
        onView(waitForPlayerControl(R.id.next_chapter_button)).perform(click())
        onView(waitForPlayerControl(R.id.exo_play_pause)).check(matches(isDisplayed()))

        onView(waitForPlayerControl(R.id.previous_chapter_button)).perform(click())
        onView(waitForPlayerControl(R.id.exo_play_pause)).check(matches(isDisplayed()))
    }

    private fun textOf(matcher: Matcher<View>): String {
        var text = ""
        onView(matcher).perform(
            object : ViewAction {
                override fun getConstraints() = isDisplayed()
                override fun getDescription() = "capture text"
                override fun perform(uiController: UiController, view: View) {
                    text = (view as TextView).text.toString()
                }
            },
        )
        return text
    }
}

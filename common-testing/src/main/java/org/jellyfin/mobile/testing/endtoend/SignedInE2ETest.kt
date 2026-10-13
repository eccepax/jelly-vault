package org.jellyfin.mobile.testing.endtoend

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.hamcrest.Matcher
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.jellyfin.mobile.common.R as CommonR

const val WAIT_TIMEOUT_MS = 15_000L

/**
 * The first screen of a freshly installed app: its first start (dex optimisation, Koin, Room) can
 * take well over [WAIT_TIMEOUT_MS] on an emulator, which made each suite's first test flaky.
 */
const val FIRST_SCREEN_TIMEOUT_MS = 45_000L

/**
 * Base for e2e tests that sign in to the Vault through common's screens: the Compose connect screen,
 * then the classic-View LoginFragment (driven with Espresso).
 */
abstract class SignedInE2ETest<A : ComponentActivity>(activity: Class<A>) : VaultE2ETest() {

    @get:Rule
    val composeRule: AndroidComposeTestRule<ActivityScenarioRule<A>, A> = createAndroidComposeRule(activity)

    @Before
    fun requireCredentials() {
        assumeFalse("e2e.username not configured in local.properties — skipping", e2eUsername().isBlank())
        assumeFalse("e2e.password not configured in local.properties — skipping", e2ePassword().isBlank())
    }

    /** Connects to the Vault and submits the sign-in form. Returns once it has been submitted. */
    protected fun connectAndSignIn() {
        connectToVault()
        signIn()
    }

    /** Enters the Vault's address on the connect screen. */
    protected fun connectToVault() {
        val hostHint = context.getString(CommonR.string.host_input_hint)
        // Some apps only setContent{} once MainViewModel.serverState first emits, which races the
        // test's first interaction on a cold start: wait until the screen actually exists.
        waitForComposeText(hostHint, FIRST_SCREEN_TIMEOUT_MS)
        composeRule.onNodeWithText(hostHint).performTextInput(e2eHostname())
        composeRule.onNodeWithText(context.getString(CommonR.string.connect_button_text)).performClick()
    }

    /** Fills in and submits the sign-in form (the server is already chosen). */
    protected fun signIn() {
        waitForView(CommonR.id.editUsername)
        onView(withId(CommonR.id.editUsername)).perform(replaceText(e2eUsername()))
        onView(withId(CommonR.id.editPassword)).perform(replaceText(e2ePassword()))
        onView(withId(CommonR.id.buttonLogin)).perform(click())
    }

    protected fun waitForComposeText(text: String, timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            runCatching { composeRule.onNodeWithText(text).assertExists() }.isSuccess
        }
    }

    protected fun waitForView(viewId: Int, timeoutMillis: Long = WAIT_TIMEOUT_MS) =
        waitForView(withId(viewId), timeoutMillis)

    /** Waits until a view matching [matcher] is displayed (combine with isDisplayed() for duplicate ids). */
    protected fun waitForView(matcher: Matcher<View>, timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            runCatching { onView(matcher).check(matches(isDisplayed())) }.isSuccess
        }
    }
}

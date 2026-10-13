package org.jellyfin.mobile.music.e2e

import android.Manifest
import android.content.Context
import android.os.Build
import android.view.View
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.rule.GrantPermissionRule
import org.hamcrest.Matcher
import org.jellyfin.mobile.music.TuneMainActivity
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.jellyfin.mobile.common.R as CommonR

const val WAIT_TIMEOUT_MS = 15_000L

/**
 * Base for any e2e test that needs to start from the signed-in library. The connect screen is
 * common's Compose `ConnectScreen`; login is common's classic-View `LoginFragment` (hosted inside
 * TuneVault's Compose tree), so it's driven with Espresso.
 */
abstract class LoggedInE2ETest : BaseE2ETest() {

    @get:Rule
    val composeRule = createAndroidComposeRule<TuneMainActivity>()

    /** Pre-granted, so the system's prompt on a first download never covers the screen under test. */
    @get:Rule
    val notifications: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @Before
    fun requireCredentials() {
        assumeFalse("e2e.username not configured in local.properties — skipping", e2eUsername().isBlank())
        assumeFalse("e2e.password not configured in local.properties — skipping", e2ePassword().isBlank())
    }

    /** Connects to the Vault and logs in. Returns once the login has been submitted. */
    protected fun connectAndLogin() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val hostHint = context.getString(CommonR.string.host_input_hint)
        val connectButtonText = context.getString(CommonR.string.connect_button_text)

        waitForComposeText(hostHint)
        composeRule.onNodeWithText(hostHint).performTextInput(e2eHostname())
        composeRule.onNodeWithText(connectButtonText).performClick()

        signIn()
    }

    /** Fills in and submits the sign-in form (the server is already chosen). */
    protected fun signIn() {
        waitForView(withId(CommonR.id.editUsername))
        onView(withId(CommonR.id.editUsername)).perform(replaceText(e2eUsername()))
        onView(withId(CommonR.id.editPassword)).perform(replaceText(e2ePassword()))
        onView(withId(CommonR.id.buttonLogin)).perform(click())
    }

    /** Signs in (landing on Home) and switches to the Library tab. */
    protected fun connectAndLoginToLibrary() {
        connectAndLogin()
        waitForComposeText("Library")
        composeRule.onNodeWithText("Library").performClick()
        waitForComposeText("Albums")
    }

    /**
     * Scrolls the (first) scrollable list or grid until [text] is composed, then taps it. Lazy lists
     * only compose what's on screen, so off-screen items don't exist until scrolled to.
     */
    protected fun scrollToAndClick(text: String, timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).performClick()
    }

    /** Opens [title] from the Albums grid and waits for its track list. */
    protected fun openAlbum(title: String, firstTrack: String) {
        scrollToAndClick(title)
        waitForComposeText(firstTrack)
    }

    protected fun waitForComposeText(text: String, timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            runCatching { composeRule.onNodeWithText(text).assertExists() }.isSuccess
        }
    }

    protected fun waitForView(matcher: Matcher<View>, timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            runCatching { onView(matcher).check(matches(isDisplayed())) }.isSuccess
        }
    }
}

package org.jellyfin.mobile.testing.endtoend

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.DeviceInfo
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

private const val QUICK_CONNECT_CODE_LENGTH = 6

/**
 * Base for e2e tests that sign in to the Vault through common's screens, the connect screen then
 * the sign-in screen.
 */
abstract class SignedInE2ETest<A : ComponentActivity>(activity: Class<A>) : VaultE2ETest() {

    @get:Rule
    val composeRule: AndroidComposeTestRule<ActivityScenarioRule<A>, A> = createAndroidComposeRule(activity)

    @Before
    fun requireCredentials() {
        assumeFalse("no test server username (VAULT_TEST_USERNAME or e2e.username) — skipping", e2eUsername().isBlank())
        assumeFalse("no test server password (VAULT_TEST_PASSWORD or e2e.password) — skipping", e2ePassword().isBlank())
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
        waitForComposeTag(VaultTestTags.LOGIN_USERNAME)
        composeRule.onNodeWithTag(VaultTestTags.LOGIN_USERNAME).performTextInput(e2eUsername())
        composeRule.onNodeWithTag(VaultTestTags.LOGIN_PASSWORD).performTextInput(e2ePassword())
        // The title says "Sign in" too, so the button is the one that can be pressed
        composeRule.onNode(hasText(context.getString(CommonR.string.login_button)) and hasClickAction()).performClick()
    }

    /** Opens Quick Connect on the sign-in screen and returns the code it shows. */
    protected fun startQuickConnect(): String {
        val quickConnect = context.getString(CommonR.string.login_quick_connect)
        waitForComposeText(quickConnect)
        composeRule.onNodeWithText(quickConnect).performClick()
        var code = ""
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            code = composeRule.onAllNodesWithTag(VaultTestTags.QUICK_CONNECT_CODE).fetchSemanticsNodes()
                .firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("")?.filter(Char::isDigit)
                .orEmpty()
            code.length == QUICK_CONNECT_CODE_LENGTH
        }
        return code
    }

    /** Waits until the sign-in screen shows, as it does after signing out. */
    protected fun waitForSignInScreen(timeoutMillis: Long = WAIT_TIMEOUT_MS) =
        waitForComposeTag(VaultTestTags.LOGIN_USERNAME, timeoutMillis)

    protected fun waitForComposeTag(tag: String, timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            // Between one screen and the next (signing out starts the app over) there's briefly no
            // Compose content at all, which throws rather than finding nothing: not there yet either
            runCatching { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }

    /**
     * Approves [code] as the e2e user, from a separate signed-in device, the way someone would from
     * their phone or the web client's profile page.
     */
    protected fun approveQuickConnect(code: String) {
        asTestUserElsewhere("vault-e2e-quickconnect-approver") { it.quickConnectApi.authorizeQuickConnect(code) }
    }

    /**
     * Runs [block] signed in as the e2e user on another device ([deviceId]), not through the app:
     * what someone would do from their phone or the web client. Signs that device out afterwards.
     */
    protected fun <T> asTestUserElsewhere(deviceId: String, block: suspend (ApiClient) -> T): T = runBlocking {
        val elsewhere = koinGet<Jellyfin>().createApi(baseUrl = e2eHostname(), deviceInfo = DeviceInfo(deviceId, "e2e"))
        val auth = elsewhere.userApi.authenticateUserByName(e2eUsername(), e2ePassword()).content
        elsewhere.update(accessToken = auth.accessToken)
        try {
            block(elsewhere)
        } finally {
            elsewhere.sessionApi.reportSessionEnded()
        }
    }

    protected fun waitForComposeText(text: String, timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            runCatching { composeRule.onNodeWithText(text).assertExists() }.isSuccess
        }
    }
}

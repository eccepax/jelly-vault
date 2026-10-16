package org.jellyfin.mobile.common.setup

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: LoginUiState = LoginUiState(), actions: LoginActions = LoginActions()) {
        composeRule.setContent { AppTheme(VaultBrand.Jellyfin) { LoginScreen(state, actions) } }
    }

    private fun string(resId: Int) = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    // The title says "Sign in" too, so the button is the one that can be pressed
    private fun signInButton() = composeRule.onNode(hasText(string(R.string.login_button)) and hasClickAction())

    @Test
    fun `signing in hands over the username and password typed`() {
        var entered: Pair<String, String>? = null
        show(actions = LoginActions(onSignIn = { user, password -> entered = user to password }))

        composeRule.onNodeWithText(string(R.string.login_username)).performTextInput("alice")
        composeRule.onNodeWithText(string(R.string.login_password)).performTextInput("secret")
        signInButton().performClick()

        entered shouldBe ("alice" to "secret")
    }

    @Test
    fun `the password is hidden`() {
        show()

        composeRule.onNodeWithTag(VaultTestTags.LOGIN_PASSWORD)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    }

    @Test
    fun `while signing in, nothing can be changed and it shows progress`() {
        show(LoginUiState(busy = true))

        signInButton().assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.login_quick_connect)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.login_change_server)).assertIsNotEnabled()
        composeRule.onNodeWithTag(VaultTestTags.LOGIN_USERNAME).assertIsNotEnabled()
        composeRule.onNodeWithTag(VaultTestTags.LOADING).assertExists()
    }

    @Test
    fun `a failed sign-in says why`() {
        show(LoginUiState(error = LoginMessage(R.string.login_error_failed, "Invalid username or password")))

        composeRule.onNodeWithText("Invalid username or password").assertExists()
    }

    @Test
    fun `without a reason from the server, the message is the app's own`() {
        show(LoginUiState(error = LoginMessage(R.string.login_error_timeout)))

        composeRule.onNodeWithText(string(R.string.login_error_timeout)).assertExists()
    }

    @Test
    fun `quick connect shows its code and how to approve it`() {
        show(LoginUiState(quickConnectCode = "123456"))

        composeRule.onNodeWithTag(VaultTestTags.QUICK_CONNECT_CODE).assert(hasText("123456"))
        composeRule.onNodeWithText(string(R.string.login_quick_connect_hint)).assertExists()
    }

    @Test
    fun `quick connect and change server do what they say`() {
        val tapped = mutableListOf<String>()
        show(
            actions = LoginActions(
                onQuickConnect = { tapped += "quick connect" },
                onChangeServer = { tapped += "change server" },
            ),
        )

        composeRule.onNodeWithText(string(R.string.login_quick_connect)).performClick()
        composeRule.onNodeWithText(string(R.string.login_change_server)).performClick()

        tapped shouldBe listOf("quick connect", "change server")
    }
}

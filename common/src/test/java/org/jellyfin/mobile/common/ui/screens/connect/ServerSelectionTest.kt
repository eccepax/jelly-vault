package org.jellyfin.mobile.common.ui.screens.connect

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.jellyfin.mobile.common.setup.ConnectionHelper
import org.jellyfin.mobile.common.ui.state.CheckUrlState
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The first screen of every app: typing, or picking, the server to sign in to. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ServerSelectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val apiClientController = mockk<ApiClientController>(relaxed = true)
    private val connectionHelper = mockk<ConnectionHelper>(relaxed = true)
    private val connectedTo = mutableListOf<String>()

    private fun show(
        savedServer: ServerEntity? = null,
        previousServers: List<ServerEntity> = emptyList(),
        discovered: Flow<ServerDiscoveryInfo> = emptyFlow(),
        showExternalConnectionError: Boolean = false,
    ) {
        coEvery { apiClientController.loadSavedServer() } returns savedServer
        coEvery { apiClientController.loadPreviouslyUsedServers() } returns previousServers
        every { connectionHelper.discoverServersAsFlow() } returns discovered
        composeRule.setContent {
            AppTheme(VaultBrand.Jellyfin) {
                ServerSelection(
                    showExternalConnectionError = showExternalConnectionError,
                    apiClientController = apiClientController,
                    connectionHelper = connectionHelper,
                    onConnected = { address -> connectedTo += address },
                )
            }
        }
    }

    private fun string(resId: Int) = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    private fun addressField() = composeRule.onNode(hasSetTextAction())
    private fun connectButton() = composeRule.onNodeWithText(string(R.string.connect_button_text))
    private fun chooseServerButton() = composeRule.onNodeWithText(string(R.string.choose_server_button_text))

    private fun serverAnswers(entered: String, address: String = entered) {
        coEvery { connectionHelper.checkServerUrl(entered) } returns CheckUrlState.Success(address)
    }

    private fun discovered(name: String, address: String) = mockk<ServerDiscoveryInfo> {
        every { this@mockk.name } returns name
        every { this@mockk.address } returns address
    }

    // --- typing an address -----------------------------------------------------------------------

    @Test
    fun `the last server used is filled in`() {
        show(savedServer = ServerEntity("https://vault.example.com"))

        addressField().assertTextContains("https://vault.example.com")
    }

    @Test
    fun `connect can't be pressed until an address is typed`() {
        show()

        connectButton().assertIsNotEnabled()
        addressField().performTextInput("vault.example.com")
        connectButton().assertIsEnabled()
    }

    @Test
    fun `connecting to a server that answers hands over the address it answered on`() {
        serverAnswers("vault.example.com", address = "https://vault.example.com")
        show()

        addressField().performTextInput("vault.example.com")
        connectButton().performClick()
        composeRule.waitForIdle()

        connectedTo shouldContainExactly listOf("https://vault.example.com")
    }

    @Test
    fun `a server that can't be reached shows why, and doesn't connect`() {
        coEvery { connectionHelper.checkServerUrl(any()) } returns CheckUrlState.Error("Server not found")
        show()

        addressField().performTextInput("nowhere.example.com")
        connectButton().performClick()

        composeRule.onNodeWithText("Server not found").assertExists()
        connectedTo shouldBe emptyList()
    }

    @Test
    fun `changing the address clears the error`() {
        coEvery { connectionHelper.checkServerUrl(any()) } returns CheckUrlState.Error("Server not found")
        show()
        addressField().performTextInput("nowhere.example.com")
        connectButton().performClick()

        addressField().performTextReplacement("vault.example.com")

        composeRule.onNodeWithText("Server not found").assertDoesNotExist()
    }

    @Test
    fun `while the server is checked, progress shows instead of the buttons`() {
        val answer = CompletableDeferred<CheckUrlState>()
        coEvery { connectionHelper.checkServerUrl(any()) } coAnswers { answer.await() }
        show()

        addressField().performTextInput("vault.example.com")
        connectButton().performClick()

        connectButton().assertDoesNotExist()
        chooseServerButton().assertDoesNotExist()

        answer.complete(CheckUrlState.Error("Server not found"))
        composeRule.waitForIdle()
        connectButton().assertExists()
    }

    @Test
    fun `a connection lost elsewhere is explained until the address is changed`() {
        show(savedServer = ServerEntity("https://vault.example.com"), showExternalConnectionError = true)
        val cannotConnect = string(R.string.connection_error_cannot_connect)

        composeRule.onNodeWithText(cannotConnect).assertExists()
        addressField().performTextInput("/")
        composeRule.onNodeWithText(cannotConnect).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `pressing Enter connects, checking the server once`() {
        serverAnswers("vault.example.com")
        show()
        addressField().performTextInput("vault.example.com")

        addressField().requestFocus()
        addressField().performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        coVerify(exactly = 1) { connectionHelper.checkServerUrl("vault.example.com") }
        connectedTo shouldContainExactly listOf("vault.example.com")
    }

    // --- choosing a server -----------------------------------------------------------------------

    @Test
    fun `choosing a server lists the servers found nearby first, then the ones used before`() {
        show(
            previousServers = listOf(ServerEntity("https://old.example.com")),
            discovered = flowOf(discovered("Living room", "http://192.168.4.32:8096")),
        )

        chooseServerButton().performClick()

        composeRule.onNodeWithText(string(R.string.available_servers_title)).assertExists()
        val shown = composeRule.onAllNodesWithText("http", substring = true).fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString() }
        // Each entry reads as its name, then its address (a server used before is named by its address)
        shown shouldContainExactly listOf(
            "Living room, http://192.168.4.32:8096",
            "https://old.example.com, https://old.example.com",
        )
    }

    @Test
    fun `a server found nearby that was also used before is listed once`() {
        show(
            previousServers = listOf(ServerEntity("http://192.168.4.32:8096")),
            discovered = flowOf(discovered("Living room", "http://192.168.4.32:8096")),
        )

        chooseServerButton().performClick()

        composeRule.onAllNodesWithText("http://192.168.4.32:8096").fetchSemanticsNodes().size shouldBe 1
        composeRule.onNodeWithText("Living room").assertExists()
    }

    @Test
    fun `picking a server from the list connects to it`() {
        serverAnswers("http://192.168.4.32:8096")
        show(discovered = flowOf(discovered("Living room", "http://192.168.4.32:8096")))

        chooseServerButton().performClick()
        composeRule.onNodeWithText("Living room").performClick()
        composeRule.waitForIdle()

        connectedTo shouldContainExactly listOf("http://192.168.4.32:8096")
    }

    @Test
    fun `going back from the list returns to typing an address`() {
        show()

        chooseServerButton().performClick()
        // Screen readers announce the button by this, so it needs one
        composeRule.onNodeWithContentDescription(string(R.string.vault_back)).performClick()

        addressField().assertExists()
        composeRule.onNodeWithText(string(R.string.available_servers_title)).assertDoesNotExist()
    }
}

package org.jellyfin.mobile.navigation

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.common.ServerState
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Downloaded tracks whose files have gone are fetched again once there's a server, signed in or not. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RepairDownloadsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var repairs = 0
    private var serverState by mutableStateOf<ServerState>(ServerState.Pending)

    private fun show() = composeRule.setContent { RepairDownloadsOnceThereIsAServer(serverState) { repairs++ } }

    @Test
    fun `nothing is repaired until there's a server`() {
        show()
        serverState = ServerState.Unset
        composeRule.waitForIdle()

        repairs shouldBe 0
    }

    /** As on the sign-in screen, or browsing downloads from the connect screen with a server saved. */
    @Test
    fun `once there's a server, downloads are repaired, with no one signed in yet`() {
        show()

        serverState = ServerState.Available(ServerEntity(id = 1L, hostname = "http://vault", lastUsedTimestamp = 0L))
        composeRule.waitForIdle()

        repairs shouldBe 1
    }
}

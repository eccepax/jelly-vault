package org.jellyfin.mobile.music.navigation

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.jellyfin.mobile.common.ServerState
import org.junit.jupiter.api.Test

class RootScreenTest {

    @Test
    fun `pending server state shows the splash`() {
        resolveRootScreen(ServerState.Pending, currentUserId = null) shouldBe RootScreen.Splash
    }

    @Test
    fun `pending server state shows the splash even with a saved user`() {
        resolveRootScreen(ServerState.Pending, currentUserId = 1L) shouldBe RootScreen.Splash
    }

    @Test
    fun `no saved server shows connect`() {
        resolveRootScreen(ServerState.Unset, currentUserId = null) shouldBe RootScreen.Connect
    }

    @Test
    fun `server without a signed-in user shows login`() {
        resolveRootScreen(ServerState.Available(mockk()), currentUserId = null) shouldBe RootScreen.Login
    }

    @Test
    fun `server with a signed-in user shows the library`() {
        resolveRootScreen(ServerState.Available(mockk()), currentUserId = 1L) shouldBe RootScreen.Library
    }
}

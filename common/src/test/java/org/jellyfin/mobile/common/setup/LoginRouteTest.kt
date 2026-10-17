package org.jellyfin.mobile.common.setup

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LoginRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val mainViewModel = mockk<MainViewModel>(relaxed = true)
    private val loginViewModel = mockk<LoginViewModel>(relaxed = true) {
        every { state } returns MutableStateFlow(LoginUiState())
    }

    @Test
    fun `leaving the sign-in screen stops any quick connect`() {
        var shown by mutableStateOf(true)
        composeRule.setContent {
            AppTheme(VaultBrand.Jellyfin) {
                if (shown) LoginRoute(mainViewModel, loginViewModel)
            }
        }
        verify(exactly = 0) { loginViewModel.leave() }

        shown = false
        composeRule.waitForIdle()

        verify(exactly = 1) { loginViewModel.leave() }
    }
}

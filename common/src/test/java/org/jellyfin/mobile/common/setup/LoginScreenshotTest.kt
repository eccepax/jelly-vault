package org.jellyfin.mobile.common.setup

import android.app.Application
import androidx.fragment.app.testing.launchFragmentInContainer
import io.mockk.mockk
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.testsupport.PHONE_QUALIFIERS
import org.jellyfin.mobile.testsupport.SCREENSHOT_SDK
import org.jellyfin.mobile.testsupport.captureWithSystemBars
import org.jellyfin.sdk.api.client.ApiClient
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [SCREENSHOT_SDK], qualifiers = PHONE_QUALIFIERS)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoginScreenshotTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `sign in`() {
        startKoin {
            modules(
                module {
                    single { mockk<ApiClient>(relaxed = true) }
                    single { mockk<ApiClientController>(relaxed = true) }
                    single { mockk<AppPreferences>(relaxed = true) }
                    viewModel { mockk<MainViewModel>(relaxed = true) }
                },
            )
        }
        launchFragmentInContainer<LoginFragment>(themeResId = R.style.AppTheme).use { scenario ->
            scenario.onFragment { it.requireView().captureWithSystemBars("sign-in") }
        }
    }
}

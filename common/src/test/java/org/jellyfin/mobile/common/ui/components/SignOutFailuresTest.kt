package org.jellyfin.mobile.common.ui.components

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.jellyfin.mobile.common.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SignOutFailuresTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `each sign out that fails says so`() {
        val failures = Channel<Unit>(Channel.UNLIMITED)
        composeRule.setContent { SignOutFailures(failures.receiveAsFlow()) }
        composeRule.waitForIdle()
        ShadowToast.shownToastCount() shouldBe 0

        failures.trySend(Unit)
        composeRule.waitForIdle()

        ShadowToast.getTextOfLatestToast() shouldBe context.getString(R.string.sign_out_failed)
    }
}

package org.jellyfin.mobile.robolectric

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Proof-of-concept confirming the Robolectric + JUnit5-vintage-engine pipeline actually runs
 * before any real Fragment/Activity tests are built on top of it. Uses the plain framework
 * [Application] rather than [org.jellyfin.mobile.JellyfinApplication] so this is independent of
 * app-specific Koin/DI startup — that's a separate concern for the real Fragment tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RobolectricSmokeTest {

    @Test
    fun `robolectric provides a working application context`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context shouldNotBe null
        context.packageName.shouldNotBeBlank()
    }
}

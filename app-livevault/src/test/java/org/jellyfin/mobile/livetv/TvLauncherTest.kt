package org.jellyfin.mobile.livetv

import android.content.Context
import android.content.Intent
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** One APK for phones, tablets and Google TV (Chromecast with Google TV and its remote). */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class TvLauncherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager = context.packageManager

    private fun launchersFor(category: String): List<String> =
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            .filter { it.activityInfo.packageName == context.packageName }
            .map { it.activityInfo.name }

    @Test
    fun `shows on the Google TV home screen`() {
        launchersFor(Intent.CATEGORY_LEANBACK_LAUNCHER) shouldContain LiveMainActivity::class.java.name
    }

    @Test
    fun `shows in a phone or tablet launcher`() {
        launchersFor(Intent.CATEGORY_LAUNCHER) shouldContain LiveMainActivity::class.java.name
    }

    @Test
    fun `has a banner for the TV home screen`() {
        context.applicationInfo.banner shouldNotBe 0
    }

    /**
     * Declared explicitly: Google Play treats an undeclared touchscreen as required, which would
     * hide the app from TVs.
     */
    @Test
    fun `declares touchscreen and leanback as optional, so it installs on TVs and phones alike`() {
        val declared = packageManager.getPackageInfo(context.packageName, PackageManager.GET_CONFIGURATIONS)
            .reqFeatures.orEmpty()
            .associate { it.name to (it.flags and FeatureInfo.FLAG_REQUIRED != 0) }

        declared[PackageManager.FEATURE_TOUCHSCREEN] shouldBe false
        declared[PackageManager.FEATURE_LEANBACK] shouldBe false
    }
}

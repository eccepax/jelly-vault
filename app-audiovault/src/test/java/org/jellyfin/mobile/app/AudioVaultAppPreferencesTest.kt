package org.jellyfin.mobile.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.mobile.player.mediasegments.MediaSegmentAction
import org.jellyfin.mobile.player.mediasegments.toMediaSegmentActionsString
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs against a real [AudioVaultAppPreferences] over an in-memory [FakeSharedPreferences].
 * Robolectric provides the Android framework classes the preferences use.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AudioVaultAppPreferencesTest {

    private val fakeSharedPreferences = FakeSharedPreferences()
    private val context = mockk<Context>(relaxed = true) {
        every { getSharedPreferences(any(), any()) } returns fakeSharedPreferences
    }
    private val preferences = AudioVaultAppPreferences(context)

    @Test
    fun `it hasn't asked about notifications until it's said it has, and remembers that`() {
        preferences.askedForNotifications shouldBe false

        preferences.askedForNotifications = true

        AudioVaultAppPreferences(context).askedForNotifications shouldBe true
    }

    // --- downloadOnWifiOnly ----------------------------------------------------------------------

    @Test
    fun `downloads use any network until told to wait for Wi-Fi, and that's remembered`() {
        // The library doesn't show a download waiting for Wi-Fi: on by default, one would look stuck
        preferences.downloadOnWifiOnly shouldBe false

        preferences.downloadOnWifiOnly = true

        AudioVaultAppPreferences(context).downloadOnWifiOnly shouldBe true
    }

    @Test
    fun `the networks chosen before there was a switch carry over, and the old choice is let go`() {
        val carriedOver = listOf(OLD_WIFI_ONLY to true, OLD_MOBILE_DATA to false, OLD_MOBILE_AND_ROAMING to false)
        for ((method, wifiOnly) in carriedOver) {
            val upgraded = FakeSharedPreferences()
            upgraded.edit().putInt(OLD_DOWNLOAD_METHOD, method).apply()
            val context = mockk<Context>(relaxed = true) {
                every { getSharedPreferences(any(), any()) } returns upgraded
            }

            AudioVaultAppPreferences(context).downloadOnWifiOnly shouldBe wifiOnly
            upgraded.contains(OLD_DOWNLOAD_METHOD) shouldBe false
        }
    }

    @Test
    fun `an old choice of networks that isn't a number is let go instead of failing every launch`() {
        val upgraded = FakeSharedPreferences()
        upgraded.edit().putString(OLD_DOWNLOAD_METHOD, "1").apply()
        val context = mockk<Context>(relaxed = true) {
            every { getSharedPreferences(any(), any()) } returns upgraded
        }

        AudioVaultAppPreferences(context).downloadOnWifiOnly shouldBe false
        upgraded.contains(OLD_DOWNLOAD_METHOD) shouldBe false
    }

    // --- mediaSegmentActions --------------------------------------------------------------------

    @Test
    fun `mediaSegmentActions defaults to asking before skipping intros and outros`() {
        val expectedDefault = mapOf(
            MediaSegmentType.INTRO to MediaSegmentAction.ASK_TO_SKIP,
            MediaSegmentType.OUTRO to MediaSegmentAction.ASK_TO_SKIP,
        ).toMediaSegmentActionsString()

        preferences.mediaSegmentActions shouldBe expectedDefault
    }

    @Test
    fun `mediaSegmentActions round-trips a stored value`() {
        val stored = mapOf(MediaSegmentType.INTRO to MediaSegmentAction.SKIP).toMediaSegmentActionsString()

        preferences.mediaSegmentActions = stored

        preferences.mediaSegmentActions shouldBe stored
    }

    // --- simple flag defaults (each regression-guards against reading the wrong key) -----------

    @Test
    fun `exoPlayerAllowPressSpeedUp reads its own key and defaults to true`() {
        preferences.exoPlayerAllowPressSpeedUp shouldBe true

        fakeSharedPreferences.edit().putBoolean(Constants.PREF_EXOPLAYER_ALLOW_PRESS_SPEED_UP, false).apply()

        preferences.exoPlayerAllowPressSpeedUp shouldBe false
    }

    // --- librarySortOption / libraryGlobalFlattened / playbackSpeed ----------------------------

    @Test
    fun `librarySortOption defaults to TITLE and round-trips`() {
        preferences.librarySortOption shouldBe "TITLE"

        preferences.librarySortOption = "DATE_ADDED"

        preferences.librarySortOption shouldBe "DATE_ADDED"
    }

    @Test
    fun `libraryGlobalFlattened defaults to true and round-trips`() {
        preferences.libraryGlobalFlattened shouldBe true

        preferences.libraryGlobalFlattened = false

        preferences.libraryGlobalFlattened shouldBe false
    }

    @Test
    fun `playbackSpeed defaults to 1x and round-trips`() {
        preferences.playbackSpeed shouldBe 1f

        preferences.playbackSpeed = 1.5f

        preferences.playbackSpeed shouldBe 1.5f
    }

    // --- skipSilenceEnabled -----------------------------------------------------------------------

    @Test
    fun `skipSilenceEnabled defaults to false`() {
        preferences.skipSilenceEnabled shouldBe false
    }

    @Test
    fun `skipSilenceEnabled round-trips through its own key`() {
        preferences.skipSilenceEnabled = true

        preferences.skipSilenceEnabled shouldBe true
        fakeSharedPreferences.getBoolean(Constants.PREF_SKIP_SILENCE, false) shouldBe true
    }

    // --- retired settings -------------------------------------------------------------------------

    @Test
    fun `settings that no longer exist are cleared from upgraded installs`() {
        fakeSharedPreferences.edit()
            .putBoolean("pref_ignore_bluetooth_permission", true)
            .putBoolean(Constants.PREF_SKIP_SILENCE, true)
            .apply()

        val upgraded = AudioVaultAppPreferences(context)

        fakeSharedPreferences.contains("pref_ignore_bluetooth_permission") shouldBe false
        upgraded.skipSilenceEnabled shouldBe true
    }
}

/** How the networks downloads may use were kept before the Wi-Fi only switch. */
private const val OLD_DOWNLOAD_METHOD = "pref_download_method"
private const val OLD_WIFI_ONLY = 0
private const val OLD_MOBILE_DATA = 1
private const val OLD_MOBILE_AND_ROAMING = 2

private class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key] as? MutableSet<String> ?: defValues

    // As the real ones: a value of another type throws rather than reading as the default
    override fun getInt(key: String?, defValue: Int): Int = values[key]?.let { it as Int } ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableListOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { pending[key!!] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            pending[key!!] = values
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { pending[key!!] = value }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { pending[key!!] = value }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { pending[key!!] = value }
        override fun putBoolean(
            key: String?,
            value: Boolean,
        ): SharedPreferences.Editor = apply { pending[key!!] = value }
        override fun remove(key: String?): SharedPreferences.Editor = apply { removals += key!! }
        override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) values.clear()
            removals.forEach { values.remove(it) }
            values.putAll(pending)
        }
    }
}

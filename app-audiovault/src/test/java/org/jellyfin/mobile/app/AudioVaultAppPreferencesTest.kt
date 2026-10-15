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
 *
 * [downloadToInternal] must write `PREF_DOWNLOAD_INTERNAL`, not `PREF_DOWNLOAD_METHOD`; mixing them
 * up silently corrupts the download-method setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AudioVaultAppPreferencesTest {

    private val fakeSharedPreferences = FakeSharedPreferences()
    private val context = mockk<Context>(relaxed = true) {
        every { getSharedPreferences(any(), any()) } returns fakeSharedPreferences
    }
    private val preferences = AudioVaultAppPreferences(context)

    // --- downloadMethod -----------------------------------------------------------------------

    @Test
    fun `downloadMethod is null when never set`() {
        preferences.downloadMethod shouldBe null
    }

    @Test
    fun `downloadMethod round-trips a stored value`() {
        preferences.downloadMethod = 2

        preferences.downloadMethod shouldBe 2
    }

    @Test
    fun `setting downloadMethod to null does not clear a previously stored value`() {
        preferences.downloadMethod = 2

        preferences.downloadMethod = null

        preferences.downloadMethod shouldBe 2
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
    fun `musicNotificationAlwaysDismissible reads its own key and defaults to false`() {
        preferences.musicNotificationAlwaysDismissible shouldBe false

        fakeSharedPreferences.edit().putBoolean(Constants.PREF_MUSIC_NOTIFICATION_ALWAYS_DISMISSIBLE, true).apply()

        preferences.musicNotificationAlwaysDismissible shouldBe true
    }

    @Test
    fun `exoPlayerAllowPressSpeedUp reads its own key and defaults to true`() {
        preferences.exoPlayerAllowPressSpeedUp shouldBe true

        fakeSharedPreferences.edit().putBoolean(Constants.PREF_EXOPLAYER_ALLOW_PRESS_SPEED_UP, false).apply()

        preferences.exoPlayerAllowPressSpeedUp shouldBe false
    }

    @Test
    fun `exoPlayerAllowBackgroundAudio reads its own key and defaults to false`() {
        preferences.exoPlayerAllowBackgroundAudio shouldBe false

        fakeSharedPreferences.edit().putBoolean(Constants.PREF_EXOPLAYER_ALLOW_BACKGROUND_AUDIO, true).apply()

        preferences.exoPlayerAllowBackgroundAudio shouldBe true
    }

    @Test
    fun `chromecastEnabled reads its own key and defaults to false`() {
        preferences.chromecastEnabled shouldBe false

        fakeSharedPreferences.edit().putBoolean(Constants.PREF_CHROMECAST_ENABLED, true).apply()

        preferences.chromecastEnabled shouldBe true
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
}

private class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
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

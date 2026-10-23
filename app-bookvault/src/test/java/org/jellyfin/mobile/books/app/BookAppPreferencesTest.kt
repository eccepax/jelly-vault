package org.jellyfin.mobile.books.app

import android.content.Context
import android.content.SharedPreferences
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * Tests [BookAppPreferences.getLibraryViewMode]'s fallback to the default, against a real instance
 * over a mocked [Context].
 */
class BookAppPreferencesTest {

    private val fakeSharedPreferences = FakeSharedPreferences()
    private val context = mockk<Context>(relaxed = true) {
        every { getSharedPreferences(any(), any()) } returns fakeSharedPreferences
    }
    private val preferences = BookAppPreferences(context)

    @Test
    fun `a valid stored value round-trips`() {
        preferences.setLibraryViewMode("lib-1", LibraryViewMode.SHELF)

        preferences.getLibraryViewMode("lib-1") shouldBe LibraryViewMode.SHELF
    }

    @Test
    fun `a missing stored value falls back to LIST`() {
        preferences.getLibraryViewMode("never-set") shouldBe LibraryViewMode.LIST
    }

    @Test
    fun `a corrupt or unrecognized stored value falls back to LIST`() {
        // Simulates a stale value left behind by a since-renamed/removed enum constant.
        fakeSharedPreferences.edit().putString("view_mode_lib-1", "GRID").apply()

        preferences.getLibraryViewMode("lib-1") shouldBe LibraryViewMode.LIST
    }

    // --- EPUB text customization (global, not per-book/per-library) ---------------------------

    // --- downloads ------------------------------------------------------------------------------

    @Test
    fun `books download over Wi-Fi only until the user allows mobile data`() {
        preferences.downloadOnWifiOnly shouldBe true

        preferences.downloadOnWifiOnly = false

        BookAppPreferences(context).downloadOnWifiOnly shouldBe false
    }

    @Test
    fun `epubFontScale defaults to 1_0`() {
        preferences.epubFontScale shouldBe 1.0f
    }

    @Test
    fun `epubFontScale round-trips a stored value`() {
        preferences.epubFontScale = 1.25f

        preferences.epubFontScale shouldBe 1.25f
    }

    @Test
    fun `epubFontFamily defaults to SERIF`() {
        preferences.epubFontFamily shouldBe EpubFontFamily.SERIF
    }

    @Test
    fun `epubFontFamily round-trips a stored value`() {
        preferences.epubFontFamily = EpubFontFamily.MONOSPACE

        preferences.epubFontFamily shouldBe EpubFontFamily.MONOSPACE
    }

    @Test
    fun `a corrupt or unrecognized epubFontFamily value falls back to SERIF`() {
        // Simulates a stale value left behind by a since-renamed/removed enum constant.
        fakeSharedPreferences.edit().putString("epub_font_family", "COMIC_SANS").apply()

        preferences.epubFontFamily shouldBe EpubFontFamily.SERIF
    }

    @Test
    fun `epubLineSpacing defaults to 1_0`() {
        preferences.epubLineSpacing shouldBe 1.0f
    }

    @Test
    fun `epubLineSpacing round-trips a stored value`() {
        preferences.epubLineSpacing = 1.5f

        preferences.epubLineSpacing shouldBe 1.5f
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

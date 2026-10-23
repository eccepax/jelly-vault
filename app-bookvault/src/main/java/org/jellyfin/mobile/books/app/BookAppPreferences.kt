package org.jellyfin.mobile.books.app

import android.content.Context
import org.jellyfin.mobile.common.app.AppPreferences

class BookAppPreferences(context: Context) : AppPreferences(context) {

    fun getLibraryViewMode(libraryId: String): LibraryViewMode {
        val stored = sharedPreferences.getString("view_mode_$libraryId", null)
        return stored?.let { runCatching { LibraryViewMode.valueOf(it) }.getOrNull() } ?: LibraryViewMode.LIST
    }

    fun setLibraryViewMode(libraryId: String, mode: LibraryViewMode) {
        sharedPreferences.edit().putString("view_mode_$libraryId", mode.name).apply()
    }

    /** Books download over Wi-Fi (any unmetered network) only, unless the user allows mobile data. */
    var downloadOnWifiOnly: Boolean
        get() = sharedPreferences.getBoolean("download_wifi_only", true)
        set(value) = sharedPreferences.edit().putBoolean("download_wifi_only", value).apply()

    // --- EPUB text customization: global (applies to every book), not per-library/per-book -----

    var epubFontScale: Float
        get() = sharedPreferences.getFloat("epub_font_scale", 1.0f)
        set(value) = sharedPreferences.edit().putFloat("epub_font_scale", value).apply()

    var epubFontFamily: EpubFontFamily
        get() {
            val stored = sharedPreferences.getString("epub_font_family", null)
            return stored?.let { runCatching { EpubFontFamily.valueOf(it) }.getOrNull() } ?: EpubFontFamily.SERIF
        }
        set(value) = sharedPreferences.edit().putString("epub_font_family", value.name).apply()

    var epubLineSpacing: Float
        get() = sharedPreferences.getFloat("epub_line_spacing", 1.0f)
        set(value) = sharedPreferences.edit().putFloat("epub_line_spacing", value).apply()
}

enum class LibraryViewMode {
    LIST,
    SHELF,
}

enum class EpubFontFamily(val cssValue: String) {
    SERIF("serif"),
    SANS_SERIF("sans-serif"),
    MONOSPACE("monospace"),
}

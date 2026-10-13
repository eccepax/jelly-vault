package org.jellyfin.mobile.music.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.domain.AlbumSort
import org.jellyfin.mobile.music.domain.StreamingQuality
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TuneAppPreferencesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `album sort defaults to title`() {
        TuneAppPreferences(context).albumSort shouldBe AlbumSort.TITLE
    }

    @Test
    fun `album sort persists across instances`() {
        TuneAppPreferences(context).albumSort = AlbumSort.RECENTLY_ADDED

        TuneAppPreferences(context).albumSort shouldBe AlbumSort.RECENTLY_ADDED
    }

    @Test
    fun `an unrecognised stored sort falls back to title`() {
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            .edit().putString("album_sort", "BY_VIBES").commit()

        TuneAppPreferences(context).albumSort shouldBe AlbumSort.TITLE
    }

    @Test
    fun `streaming quality defaults to original`() {
        TuneAppPreferences(context).streamingQuality shouldBe StreamingQuality.ORIGINAL
    }

    @Test
    fun `streaming quality persists across instances`() {
        TuneAppPreferences(context).streamingQuality = StreamingQuality.LOW

        TuneAppPreferences(context).streamingQuality shouldBe StreamingQuality.LOW
    }

    @Test
    fun `downloads default to original quality, over wi-fi only`() {
        val preferences = TuneAppPreferences(context)

        preferences.downloadQuality shouldBe StreamingQuality.ORIGINAL
        preferences.downloadOnWifiOnly shouldBe true
    }

    @Test
    fun `download choices persist and are separate from streaming`() {
        TuneAppPreferences(context).apply {
            streamingQuality = StreamingQuality.LOW
            downloadQuality = StreamingQuality.HIGH
            downloadOnWifiOnly = false
        }

        TuneAppPreferences(context).downloadQuality shouldBe StreamingQuality.HIGH
        TuneAppPreferences(context).downloadOnWifiOnly shouldBe false
        TuneAppPreferences(context).streamingQuality shouldBe StreamingQuality.LOW
    }

    @Test
    fun `remembers having asked to show notifications`() {
        TuneAppPreferences(context).askedForNotifications shouldBe false

        TuneAppPreferences(context).askedForNotifications = true

        TuneAppPreferences(context).askedForNotifications shouldBe true
    }

    @Test
    fun `the sleep timer is shown until hidden`() {
        TuneAppPreferences(context).showSleepTimer shouldBe true

        TuneAppPreferences(context).showSleepTimer = false

        TuneAppPreferences(context).showSleepTimer shouldBe false
    }

    @Test
    fun `volume levelling is on until turned off`() {
        TuneAppPreferences(context).volumeLevelling shouldBe true

        TuneAppPreferences(context).volumeLevelling = false

        TuneAppPreferences(context).volumeLevelling shouldBe false
    }
}

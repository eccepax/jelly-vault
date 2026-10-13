package org.jellyfin.mobile.music.downloads

import android.app.Application
import android.net.Uri
import androidx.media3.datasource.DataSpec
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TrackCacheKeyTest {

    private fun keyOf(uri: String) = TrackCacheKey.factory.buildCacheKey(DataSpec(Uri.parse(uri)))

    @Test
    fun `a song's stream is keyed by its id, whatever the quality or token`() {
        val original = keyOf("http://vault:8096/Audio/0a1b2c/universal?maxStreamingBitrate=140000000&api_key=old")
        val dataSaver = keyOf("http://other:8096/Audio/0a1b2c/universal?maxStreamingBitrate=128000&api_key=new")

        original shouldBe "track:0a1b2c"
        dataSaver shouldBe original
    }

    @Test
    fun `the id is found in any song url`() {
        TrackCacheKey.trackIdOf("http://vault/Audio/abc/stream?static=true") shouldBe "abc"
    }

    @Test
    fun `anything else is keyed by its whole url`() {
        keyOf("http://vault/Items/abc/Images/Primary") shouldBe "http://vault/Items/abc/Images/Primary"
        TrackCacheKey.trackIdOf("http://vault/Items/abc/Images/Primary") shouldBe null
    }
}

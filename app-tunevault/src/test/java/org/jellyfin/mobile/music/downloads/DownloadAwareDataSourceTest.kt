package org.jellyfin.mobile.music.downloads

import android.app.Application
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.test.utils.FakeDataSet
import androidx.media3.test.utils.FakeDataSource
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadAwareDataSourceTest {

    private val songUri = "http://vault/Audio/a/universal?api_key=t"
    private val serverData = FakeDataSet().setData(songUri, "streamed".toByteArray())
    private val onDevice = FakeDataSource.Factory().setFakeDataSet(
        FakeDataSet().setData(songUri, "downloaded".toByteArray()),
    )
    private val server = FakeDataSource.Factory().setFakeDataSet(serverData)
    private var downloaded = setOf<String>()

    private fun read(uri: String = songUri): String {
        val source: DataSource =
            DownloadAwareDataSource.Factory(isDownloaded = { it in downloaded }, onDevice = onDevice, server = server)
                .createDataSource()
        source.open(DataSpec(Uri.parse(uri)))
        return try {
            String(DataSourceUtil.readToEnd(source))
        } finally {
            source.close()
        }
    }

    @Test
    fun `a downloaded song plays from the device`() {
        downloaded = setOf("a")

        read() shouldBe "downloaded"
    }

    @Test
    fun `a song not fully downloaded streams, never mixing a partial file with the stream`() {
        downloaded = emptySet()

        read() shouldBe "streamed"
    }

    @Test
    fun `anything that isn't a song streams`() {
        downloaded = setOf("a")
        serverData.setData("http://vault/Items/a/Images/Primary", "art".toByteArray())

        read("http://vault/Items/a/Images/Primary") shouldBe "art"
    }
}

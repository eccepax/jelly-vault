package org.jellyfin.mobile.common.downloads

import android.app.Application
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.test.utils.FakeDataSet
import androidx.media3.test.utils.FakeDataSource
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadAwareDataSourceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val songUri = "http://vault/Audio/a/universal?api_key=t"
    private val serverData = FakeDataSet().setData(songUri, "streamed".toByteArray())
    private val server = FakeDataSource.Factory().setFakeDataSet(serverData)
    private val downloadedFile by lazy { folder.newFile("a").apply { writeText("downloaded") } }
    private var downloaded = setOf<String>()

    private fun read(uri: String = songUri, position: Long = 0): String {
        val source: DataSource = DownloadAwareDataSource.Factory(
            // How an app finds the item in its URLs; here, a Jellyfin /Audio/{id}/... stream
            itemIdOf = { uri -> uri.pathSegments.takeIf { it.firstOrNull() == "Audio" }?.getOrNull(1) },
            fileFor = { itemId -> downloadedFile.takeIf { itemId in downloaded } },
            onDevice = FileDataSource.Factory(),
            server = server,
        ).createDataSource()
        source.open(DataSpec.Builder().setUri(Uri.parse(uri)).setPosition(position).build())
        return try {
            String(DataSourceUtil.readToEnd(source))
        } finally {
            source.close()
        }
    }

    @Test
    fun `a downloaded item plays from the device`() {
        downloaded = setOf("a")

        read() shouldBe "downloaded"
    }

    @Test
    fun `an item not fully downloaded streams, never mixing a partial file with the stream`() {
        downloaded = emptySet()

        read() shouldBe "streamed"
    }

    @Test
    fun `seeking in a downloaded item reads from that point of the file`() {
        downloaded = setOf("a")

        read(position = 4) shouldBe "loaded"
    }

    @Test
    fun `anything that isn't a downloadable item streams`() {
        downloaded = setOf("a")
        serverData.setData("http://vault/Items/a/Images/Primary", "art".toByteArray())

        read("http://vault/Items/a/Images/Primary") shouldBe "art"
    }
}

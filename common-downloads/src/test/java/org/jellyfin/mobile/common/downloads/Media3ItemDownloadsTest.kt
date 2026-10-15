package org.jellyfin.mobile.common.downloads

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.test.utils.robolectric.RobolectricUtil.runMainLooperUntil
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.jellyfin.mobile.common.downloads.ItemDownload.State
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowNetworkCapabilities

/** A real Media3 DownloadManager fetching from a local web server into plain files. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class Media3ItemDownloadsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val server = MockWebServer().apply { start() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val content = ByteArray(32 * 1024) { (it % 239).toByte() }
    private var serviceStarts = 0

    private val files by lazy { DownloadFiles(folder.newFolder("downloads")) }
    private val manager by lazy {
        fileDownloadManager(
            context = context,
            databaseProvider = StandaloneDatabaseProvider(context),
            dataSourceFactory = DefaultHttpDataSource.Factory(),
            files = files,
            retries = 0,
        )
    }
    private val downloads by lazy { Media3ItemDownloads(manager, files, scope, onWork = { serviceStarts++ }) }

    @Before
    fun connectToWifi() {
        // Media3 only downloads on a validated network, which Robolectric doesn't report by default.
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val wifi = ShadowNetworkCapabilities.newInstance()
        shadowOf(wifi).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(wifi).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(wifi).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(connectivity).setNetworkCapabilities(connectivity.activeNetwork, wifi)
    }

    @After
    fun tearDown() {
        manager.release()
        scope.cancel()
        server.shutdown()
    }

    private fun url(id: String) = server.url("/Items/$id/Download").toString()

    private fun stateOf(id: String) = downloads.downloads.value[id]?.state

    @Test
    fun `is ready once the manager has read what it holds`() {
        runMainLooperUntil { downloads.isReady.value }
    }

    @Test
    fun `a downloaded item ends up as a whole file`() {
        server.enqueue(MockResponse().setBody(Buffer().write(content)))
        runMainLooperUntil { downloads.isReady.value }

        downloads.download("book-1", url("book-1"))
        runMainLooperUntil { stateOf("book-1") == State.DONE }

        downloads.fileFor("book-1").shouldNotBeNull().readBytes() shouldBe content
        downloads.downloads.value.getValue("book-1").bytesDownloaded shouldBe content.size.toLong()
    }

    @Test
    fun `asking for a download keeps the download service going`() {
        server.enqueue(MockResponse().setBody(Buffer().write(content)))
        runMainLooperUntil { downloads.isReady.value }

        downloads.download("book-1", url("book-1"))

        serviceStarts shouldBe 1
    }

    @Test
    fun `an item the server can't send fails and has no file`() {
        server.enqueue(MockResponse().setResponseCode(500))
        runMainLooperUntil { downloads.isReady.value }

        downloads.download("book-1", url("book-1"))
        runMainLooperUntil { stateOf("book-1") == State.FAILED }

        downloads.fileFor("book-1").shouldBeNull()
    }

    @Test
    fun `nothing is found for an item never downloaded`() {
        runMainLooperUntil { downloads.isReady.value }

        downloads.fileFor("never").shouldBeNull()
    }

    @Test
    fun `removing an item deletes its file`() {
        server.enqueue(MockResponse().setBody(Buffer().write(content)))
        runMainLooperUntil { downloads.isReady.value }
        downloads.download("book-1", url("book-1"))
        runMainLooperUntil { stateOf("book-1") == State.DONE }
        val file = downloads.fileFor("book-1").shouldNotBeNull()

        downloads.remove("book-1")
        runMainLooperUntil { stateOf("book-1") == null }
        downloads.fileFor("book-1").shouldBeNull()

        // The manager deletes the file on its own thread, just after the item leaves the list.
        runMainLooperUntil { !file.exists() }
    }
}

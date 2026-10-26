package org.jellyfin.mobile.music.account

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.common.downloads.DownloadSpace
import org.jellyfin.mobile.music.app.FakePlaybackPreferences
import org.jellyfin.mobile.music.catalog.Account
import org.jellyfin.mobile.music.catalog.AccountInfo
import org.jellyfin.mobile.music.domain.StreamingQuality
import org.jellyfin.mobile.music.playback.SetVolumeLevelling
import org.jellyfin.mobile.music.playback.ShowSleepTimer
import org.jellyfin.mobile.testing.downloads.FakeDownloadSettings
import org.jellyfin.sdk.model.ClientInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val vault = Account(userName = "alice", serverName = "The Vault", serverUrl = "http://vault:8096")
    private var accountFailure: Exception? = null
    private val accountInfo = object : AccountInfo {
        override suspend fun current(): Account = accountFailure?.let { throw it } ?: vault
    }
    private val preferences = FakePlaybackPreferences()
    private val downloads = FakeDownloadSettings()
    private val sleepTimerShown = mutableListOf<Boolean>()
    private val sleepTimer = ShowSleepTimer { sleepTimerShown += it }
    private val levellingSet = mutableListOf<Boolean>()
    private val levelling = SetVolumeLevelling { levellingSet += it }
    private var signOuts = 0
    private var signOutFinishes: CompletableDeferred<Unit>? = null
    private var signOutFails = false

    private fun viewModel() = SettingsViewModel(
        accountInfo = accountInfo,
        preferences = preferences,
        downloads = downloads,
        sleepTimer = sleepTimer,
        levelling = levelling,
        signOut = {
            signOuts++
            signOutFinishes?.await()
            if (signOutFails) error("Couldn't forget the account")
        },
        app = ClientInfo("TuneVault", "1.2.3"),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shows who is signed in, where, and the app version`() {
        val state = viewModel().state.value

        state.account shouldBe vault
        // The app's own name and version, as the server knows it and the bug form lists it
        state.appName shouldBe "TuneVault"
        state.appVersion shouldBe "1.2.3"
    }

    @Test
    fun `an account that can't be loaded still lets you sign out`() {
        accountFailure = IOException("offline")
        val viewModel = viewModel()

        viewModel.state.value.account shouldBe null
        viewModel.signOut()

        signOuts shouldBe 1
    }

    @Test
    fun `shows the saved streaming quality`() {
        preferences.streamingQuality = StreamingQuality.NORMAL

        viewModel().state.value.streamingQuality shouldBe StreamingQuality.NORMAL
    }

    @Test
    fun `choosing a streaming quality saves it`() {
        val viewModel = viewModel()

        viewModel.setStreamingQuality(StreamingQuality.LOW)

        preferences.streamingQuality shouldBe StreamingQuality.LOW
        viewModel.state.value.streamingQuality shouldBe StreamingQuality.LOW
    }

    @Test
    fun `signing out shows progress, then reports it's done`() {
        signOutFinishes = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.signOut()
        viewModel.state.value.signingOut shouldBe true
        viewModel.signedOut.value shouldBe false

        signOutFinishes!!.complete(Unit)
        viewModel.signedOut.value shouldBe true
    }

    @Test
    fun `a sign out that fails says so, and can be tried again`() {
        signOutFails = true
        val viewModel = viewModel()
        var failures = 0
        val watching = TestScope(UnconfinedTestDispatcher()).backgroundScope
        watching.launch { viewModel.signOutFailures.collect { failures++ } }

        viewModel.signOut()
        failures shouldBe 1
        viewModel.state.value.signingOut shouldBe false
        viewModel.signedOut.value shouldBe false

        signOutFails = false
        viewModel.signOut()
        signOuts shouldBe 2
        viewModel.signedOut.value shouldBe true
    }

    @Test
    fun `tapping sign out twice signs out once`() {
        signOutFinishes = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.signOut()
        viewModel.signOut()

        signOuts shouldBe 1
    }

    @Test
    fun `shows the download choices and the space downloads use`() {
        preferences.downloadQuality = StreamingQuality.HIGH
        downloads.bytesUsed.value = 1_500_000_000L

        val viewModel = viewModel()
        val state = viewModel.state.value

        state.downloadQuality shouldBe StreamingQuality.HIGH
        state.downloadOnWifiOnly shouldBe true
        viewModel.spaceShown() shouldBe DownloadSpace(bytes = 1_500_000_000L, hasDownloads = true)
    }

    @Test
    fun `choosing a download quality saves it, leaving streaming alone`() {
        val viewModel = viewModel()

        viewModel.setDownloadQuality(StreamingQuality.NORMAL)

        preferences.downloadQuality shouldBe StreamingQuality.NORMAL
        preferences.streamingQuality shouldBe StreamingQuality.ORIGINAL
        viewModel.state.value.downloadQuality shouldBe StreamingQuality.NORMAL
    }

    @Test
    fun `downloads stay on sign-out by default, and that can be changed`() {
        val viewModel = viewModel()
        viewModel.state.value.removeDownloadsOnSignOut shouldBe false

        viewModel.setRemoveDownloadsOnSignOut(true)

        preferences.removeDownloadsOnSignOut shouldBe true
        viewModel.state.value.removeDownloadsOnSignOut shouldBe true
    }

    @Test
    fun `turning wi-fi only off saves it and lets waiting downloads go`() {
        val viewModel = viewModel()

        viewModel.setDownloadOnWifiOnly(false)

        downloads.wifiOnly shouldBe false
        downloads.events shouldBe listOf("wifiOnly false")
        viewModel.state.value.downloadOnWifiOnly shouldBe false
    }

    @Test
    fun `removing all downloads frees the space`() {
        downloads.bytesUsed.value = 42L
        val viewModel = viewModel()

        viewModel.removeAllDownloads()

        downloads.events shouldBe listOf("removeAll")
        viewModel.spaceShown() shouldBe DownloadSpace(bytes = 0L, hasDownloads = false)
    }

    @Test
    fun `removing all downloads that fails says so, and can be tried again`() {
        downloads.removeFails = true
        val viewModel = viewModel()
        val failures = mutableListOf<Unit>()
        CoroutineScope(Dispatchers.Main).launch { viewModel.removeDownloadsFailures.collect { failures += it } }

        viewModel.removeAllDownloads()
        failures.size shouldBe 1

        downloads.removeFails = false
        viewModel.removeAllDownloads()
        failures.size shouldBe 1
        downloads.events shouldBe listOf("removeAll")
    }

    @Test
    fun `downloads waiting for wi-fi can be removed though they take no space yet`() {
        downloads.queued.value = true

        viewModel().spaceShown() shouldBe DownloadSpace(bytes = 0L, hasDownloads = true)
    }

    @Test
    fun `the space isn't followed while settings isn't shown`() {
        viewModel()

        downloads.bytesUsed.subscriptionCount.value shouldBe 0
    }

    /** The space downloads take, as the screen sees it: it's followed only while shown. */
    private fun SettingsViewModel.spaceShown(): DownloadSpace? {
        val shown = downloadSpace.launchIn(CoroutineScope(Dispatchers.Main))
        return downloadSpace.value.also { shown.cancel() }
    }

    @Test
    fun `shows whether the sleep timer is shown, and hiding it goes through the timer`() {
        preferences.showSleepTimer = true
        val viewModel = viewModel()
        viewModel.state.value.showSleepTimer shouldBe true

        viewModel.setShowSleepTimer(false)

        sleepTimerShown shouldBe listOf(false)
        viewModel.state.value.showSleepTimer shouldBe false
    }

    @Test
    fun `shows whether volume levelling is on, and changes it`() {
        preferences.volumeLevelling = true
        val viewModel = viewModel()
        viewModel.state.value.volumeLevelling shouldBe true

        viewModel.setVolumeLevelling(false)

        levellingSet shouldBe listOf(false)
        viewModel.state.value.volumeLevelling shouldBe false
    }
}

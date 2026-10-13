package org.jellyfin.mobile.music.downloads

import android.app.Application
import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.scheduler.Requirements
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.downloads.TrackDownload.State
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadStateMappingTest {

    private fun download(state: Int, bytes: Long = 0L, stopReason: Int = Download.STOP_REASON_NONE) = Download(
        DownloadRequest.Builder("a", Uri.parse("http://vault/Audio/a/universal")).build(),
        state,
        0L,
        0L,
        C_LENGTH_UNSET,
        stopReason,
        // Media3 insists a failed download says why, and only a failed one.
        if (state == Download.STATE_FAILED) Download.FAILURE_REASON_UNKNOWN else Download.FAILURE_REASON_NONE,
    ).let { base ->
        // bytesDownloaded lives in DownloadProgress, set through the full constructor.
        Download(
            base.request,
            base.state,
            base.startTimeMs,
            base.updateTimeMs,
            base.contentLength,
            base.stopReason,
            base.failureReason,
            androidx.media3.exoplayer.offline.DownloadProgress().apply { bytesDownloaded = bytes },
        )
    }

    private fun stateOf(download: Download, notMet: Int = 0) = download.toTrackDownload(
        notMetRequirements = notMet,
    ).state

    @Test
    fun `media3 states map onto the app's`() {
        stateOf(download(Download.STATE_QUEUED)) shouldBe State.QUEUED
        stateOf(download(Download.STATE_DOWNLOADING)) shouldBe State.DOWNLOADING
        stateOf(download(Download.STATE_COMPLETED)) shouldBe State.DONE
        stateOf(download(Download.STATE_FAILED)) shouldBe State.FAILED
        stateOf(download(Download.STATE_RESTARTING)) shouldBe State.QUEUED
    }

    @Test
    fun `queued while a requirement like wi-fi is unmet is waiting`() {
        stateOf(download(Download.STATE_QUEUED), notMet = Requirements.NETWORK_UNMETERED) shouldBe State.WAITING
    }

    @Test
    fun `a finished song isn't waiting just because wi-fi is off`() {
        stateOf(download(Download.STATE_COMPLETED), notMet = Requirements.NETWORK_UNMETERED) shouldBe State.DONE
    }

    @Test
    fun `stopped is waiting`() {
        stateOf(download(Download.STATE_STOPPED, stopReason = 1)) shouldBe State.WAITING
    }

    @Test
    fun `carries the song id and bytes so far`() {
        val mapped = download(Download.STATE_DOWNLOADING, bytes = 4_096L).toTrackDownload(notMetRequirements = 0)

        mapped.trackId shouldBe "a"
        mapped.bytesDownloaded shouldBe 4_096L
    }

    private companion object {
        const val C_LENGTH_UNSET = -1L
    }
}

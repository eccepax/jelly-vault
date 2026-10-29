package org.jellyfin.mobile.cast

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.cast.MediaStatus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The cast device's failures, which Media3's cast player never reports: heard from the Cast
 * framework's statuses, and given to a session's cast player as its error.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
@Config(application = Application::class)
class CastFailuresTest {

    private var failures = 0
    private val watch = CastFailureWatch { failures++ }

    private val exo = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build()
    private var remote = true

    /** Stands in for Media3's cast player: playing there while [remote], and never with an error of its own. */
    private val castPlayer = object : ForwardingPlayer(exo) {
        override fun getDeviceInfo(): DeviceInfo =
            if (remote) DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build() else DeviceInfo.UNKNOWN
    }
    private val reporting = CastFailureReporting(castPlayer)

    @After
    fun tearDown() {
        exo.release()
    }

    @Test
    fun `the cast device going idle by an error is a failure`() {
        watch.onStatus(MediaStatus.PLAYER_STATE_PLAYING, MediaStatus.IDLE_REASON_NONE)
        watch.onStatus(MediaStatus.PLAYER_STATE_IDLE, MediaStatus.IDLE_REASON_ERROR)

        failures shouldBe 1
    }

    @Test
    fun `the statuses that repeat a failure aren't another`() {
        watch.onStatus(MediaStatus.PLAYER_STATE_IDLE, MediaStatus.IDLE_REASON_ERROR)
        watch.onStatus(MediaStatus.PLAYER_STATE_IDLE, MediaStatus.IDLE_REASON_ERROR)

        failures shouldBe 1
    }

    @Test
    fun `failing again after playing again is another failure`() {
        watch.onStatus(MediaStatus.PLAYER_STATE_IDLE, MediaStatus.IDLE_REASON_ERROR)
        watch.onStatus(MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.IDLE_REASON_NONE)
        watch.onStatus(MediaStatus.PLAYER_STATE_IDLE, MediaStatus.IDLE_REASON_ERROR)

        failures shouldBe 2
    }

    @Test
    fun `stopping or finishing there isn't a failure`() {
        watch.onStatus(MediaStatus.PLAYER_STATE_IDLE, MediaStatus.IDLE_REASON_FINISHED)
        watch.onStatus(MediaStatus.PLAYER_STATE_IDLE, MediaStatus.IDLE_REASON_CANCELED)
        watch.onStatus(MediaStatus.PLAYER_STATE_IDLE, MediaStatus.IDLE_REASON_INTERRUPTED)

        failures shouldBe 0
    }

    @Test
    fun `a new cast device that's failed is told of, whatever the last one said`() {
        watch.onStatus(MediaStatus.PLAYER_STATE_IDLE, MediaStatus.IDLE_REASON_ERROR)
        watch.reset()
        watch.onStatus(MediaStatus.PLAYER_STATE_IDLE, MediaStatus.IDLE_REASON_ERROR)

        failures shouldBe 2
    }

    @Test
    fun `a failure there is the session player's error, and its listeners hear of it`() {
        val heard = mutableListOf<PlaybackException>()
        reporting.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    heard += error
                }
            },
        )

        reporting.failedThere()

        reporting.playerError?.errorCode shouldBe PlaybackException.ERROR_CODE_REMOTE_ERROR
        heard.single().errorCode shouldBe PlaybackException.ERROR_CODE_REMOTE_ERROR
    }

    @Test
    fun `a failure heard again before it's played again is the same error`() {
        var heard = 0
        reporting.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    heard++
                }
            },
        )

        reporting.failedThere()
        reporting.failedThere()

        heard shouldBe 1
    }

    @Test
    fun `preparing again clears the error, as ExoPlayer's is`() {
        reporting.failedThere()

        reporting.prepare()

        reporting.playerError.shouldBeNull()
    }

    @Test
    fun `a failure heard while playing here isn't this player's`() {
        remote = false

        reporting.failedThere()

        reporting.playerError.shouldBeNull()
    }

    @Test
    fun `releasing it stops it hearing of failures`() {
        var unwatched = 0
        reporting.onRelease = { unwatched++ }

        reporting.release()

        unwatched shouldBe 1
    }
}

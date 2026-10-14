package org.jellyfin.mobile.player.deviceprofile

import android.app.Application
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.DeviceProfile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Jellyfin matches a file's container against the profile's comma-separated container lists. It
 * reports `.m4b` audiobooks as container "m4b", not "mp4"; without that name every audiobook was
 * transcoded to an unseekable MP3 stream with no duration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DeviceProfileBuilderTest {

    private val profile = DeviceProfileBuilder().getDeviceProfile()

    @Test
    fun `m4b audiobooks with AAC audio are direct played`() {
        profile.directPlays(container = "m4b", audioCodec = "aac") shouldBe true
    }

    @Test
    fun `m4a files with AAC audio are direct played`() {
        profile.directPlays(container = "m4a", audioCodec = "aac") shouldBe true
    }

    private fun DeviceProfile.directPlays(container: String, audioCodec: String): Boolean =
        directPlayProfiles.any { profile ->
            container in profile.container.orEmpty().split(',') &&
                audioCodec in profile.audioCodec.orEmpty().split(',')
        }
}

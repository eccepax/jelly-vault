package org.jellyfin.mobile.player.interaction

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.jellyfin.mobile.player.PlayerHost

class PlayerLifecycleObserver(private val viewModel: PlayerHost) : DefaultLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        viewModel.setupPlayer()
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!viewModel.allowBackgroundAudio) {
            viewModel.pause()
        }
    }
}

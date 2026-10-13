package org.jellyfin.mobile.events

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.downloads.DownloadsFragment
import org.jellyfin.mobile.player.ui.PlayerFragment
import org.jellyfin.mobile.settings.SettingsFragment
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.extensions.addFragment
import org.jellyfin.mobile.utils.extensions.replaceFragmentWithBackStack
import org.jellyfin.mobile.utils.removeDownload
import org.jellyfin.mobile.utils.requestDownload
import timber.log.Timber

class ActivityEventHandler {
    private val eventsFlow = MutableSharedFlow<ActivityEvent>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun MainActivity.subscribe() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                eventsFlow.collect { event ->
                    handleEvent(event)
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun MainActivity.handleEvent(event: ActivityEvent) {
        when (event) {
            is ActivityEvent.LaunchNativePlayer -> {
                val args = Bundle().apply {
                    putParcelable(Constants.EXTRA_MEDIA_PLAY_OPTIONS, event.playOptions)
                }
                // Use replace so the library view is destroyed while the player is showing.
                // addFragment (add + backstack) kept the library view alive, meaning touches
                // on non-clickable areas (album cover) fell through to the library RecyclerView,
                // accidentally launching more players and causing multiple simultaneous streams.
                supportFragmentManager.replaceFragmentWithBackStack<PlayerFragment>(args)
            }
            is ActivityEvent.OpenUrl -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, event.uri.toUri())
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Timber.e("openIntent: %s", e.message)
                }
            }
            is ActivityEvent.DownloadFiles -> {
                lifecycleScope.launch {
                    requestDownload(event.files)
                }
            }
            is ActivityEvent.RemoveDownload -> {
                lifecycleScope.launch {
                    with(event) { removeDownload(download, force) }
                }
            }
            ActivityEvent.OpenDownloads -> {
                supportFragmentManager.addFragment<DownloadsFragment>()
            }
            ActivityEvent.RequestBluetoothPermission -> {
                lifecycleScope.launch {
                    bluetoothPermissionHelper.requestBluetoothPermissionIfNecessary()
                }
            }
            ActivityEvent.OpenSettings -> {
                supportFragmentManager.addFragment<SettingsFragment>()
            }
            ActivityEvent.SelectServer -> {
                mainViewModel.resetServer()
            }
            ActivityEvent.ResumePlayer -> {
                supportFragmentManager.replaceFragmentWithBackStack<PlayerFragment>()
            }
            ActivityEvent.ExitApp -> {
                // RemotePlayerService was removed. Without a bound playback service we can't reliably query "isPlaying" here.
                // Prefer keeping the app backgrounded so playback can continue if active.
                moveTaskToBack(false)
            }
        }
    }

    fun emit(event: ActivityEvent) {
        if (!eventsFlow.tryEmit(event)) {
            Timber.w("ActivityEvent dropped (buffer full): %s", event)
        }
    }
}

package org.jellyfin.mobile.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.databinding.FragmentDownloadsBinding
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.jellyfin.mobile.utils.withThemedContext
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.jellyfin.mobile.common.R as CommonR

class DownloadsFragment : Fragment() {
    private val viewModel: DownloadsViewModel by viewModel()
    private val activityEventHandler: ActivityEventHandler by inject()
    private val apiClient: ApiClient by inject()
    private lateinit var adapter: DownloadsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val localInflater = inflater.withThemedContext(requireContext(), CommonR.style.AppTheme_Settings)
        val binding = FragmentDownloadsBinding.inflate(localInflater, container, false)
        binding.root.applyWindowInsetsAsMargins()
        binding.toolbar.setTitle(R.string.downloads)

        (requireActivity() as? AppCompatActivity)?.apply {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        adapter = DownloadsAdapter(
            apiClient,
            onItemClick = { download -> onDownloadItemClick(download) },
            onItemHold = { download -> onDownloadItemHold(download) },
        )
        binding.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.downloads.collect { downloads ->
                    adapter.submitList(downloads)
                }
            }
        }

        return binding.root
    }

    /** Plays [download]'s whole downloaded book, starting at [download]. */
    internal fun onDownloadItemClick(download: DownloadEntity) {
        val book = downloadedBookOf(download, viewModel.downloads.value)
        val playOptions = PlayOptions(
            ids = book.map { it.mediaSource.itemId },
            mediaSourceId = download.mediaSource.id,
            startIndex = book.indexOf(download).coerceAtLeast(0),
            startPosition = null,
            audioStreamIndex = 1,
            subtitleStreamIndex = -1,
            playFromDownloads = true,
        )
        activityEventHandler.emit(ActivityEvent.LaunchNativePlayer(playOptions))
    }

    internal fun onDownloadItemHold(download: DownloadEntity) {
        activityEventHandler.emit(ActivityEvent.RemoveDownload(download.mediaSource))
    }
}

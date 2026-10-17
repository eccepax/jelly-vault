package org.jellyfin.mobile.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.ImageType
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class DownloadsFragment : Fragment() {
    private val viewModel: DownloadsViewModel by viewModel()
    private val activityEventHandler: ActivityEventHandler by inject()
    private val apiClient: ApiClient by inject()

    private var _binding: FragmentComposeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentComposeBinding.inflate(inflater, container, false)
        return binding.composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.requestApplyInsets(binding.composeView)
        val actions = DownloadsActions(
            onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
            onPlay = ::play,
            onRemove = { activityEventHandler.emit(ActivityEvent.RemoveDownload(it.mediaSource)) },
        )
        binding.composeView.setContent {
            AppTheme(VaultBrand.AudioVault) {
                DownloadsScreen(rows(), actions)
            }
        }
    }

    @Composable
    private fun rows(): List<DownloadRow> {
        val downloads by viewModel.downloads.collectAsState()
        val context = LocalContext.current
        val coverSize = remember { resources.getDimensionPixelSize(R.dimen.movie_thumbnail_list_size) }
        return remember(downloads) {
            downloads.map { download ->
                val item = download.mediaSource.item
                DownloadRow(
                    download = download,
                    title = download.mediaSource.getName(context),
                    subtitle = listOfNotNull(item?.albumArtist, download.fileSize).joinToString(" · "),
                    coverUrl = apiClient.imageApi.getItemImageUrl(
                        itemId = download.mediaSource.itemId,
                        imageType = ImageType.PRIMARY,
                        maxWidth = coverSize,
                        maxHeight = coverSize,
                    ),
                )
            }
        }
    }

    /** Plays [download]'s whole downloaded book, starting at [download]. */
    private fun play(download: DownloadEntity) {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package org.jellyfin.mobile.library

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.fallback
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.LibraryItemBinding
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID
import kotlin.math.roundToInt

private const val PROGRESS_MAX_PERCENT = 100
private const val TICKS_PER_SECOND = 10_000_000L
private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_MINUTE = 60L
private const val HIDDEN_ITEM_ALPHA = 0.5f

/**
 * Adapter for displaying library items (audiobooks/audio) in a RecyclerView
 */
class LibraryAdapter(
    private val apiClient: ApiClient,
    private val onItemClick: (LibraryItem) -> Unit,
    private val onItemLongClick: ((LibraryItem) -> Boolean)? = null,
) : ListAdapter<LibraryItem, LibraryAdapter.LibraryItemViewHolder>(
    LibraryItemDiffCallback(),
) {
    var downloadedIds: Set<String> = emptySet()
        private set
    var hiddenIds: Set<String> = emptySet()
        private set

    /**
     * Notifies only the row(s) whose downloaded/hidden status actually changed, instead of
     * [notifyDataSetChanged], which would force every *visible* row to fully rebind (including
     * reloading its cover art via Coil) on every call — visible as list-wide flicker, especially
     * since a multi-track audiobook download fires one of these updates per chapter completing.
     */
    private inline fun notifyChangedRows(
        previous: Set<String>,
        updated: Set<String>,
        isAffected: (LibraryItem, Set<String>) -> Boolean,
    ) {
        for (position in 0 until itemCount) {
            val item = getItem(position)
            if (isAffected(item, previous) != isAffected(item, updated)) notifyItemChanged(position)
        }
    }

    fun updateDownloadedIds(ids: Set<String>) {
        if (ids == downloadedIds) return
        val previous = downloadedIds
        downloadedIds = ids
        notifyChangedRows(previous, ids) { item, downloaded -> item.isDownloaded(downloaded) }
    }

    fun updateHiddenIds(ids: Set<String>) {
        if (ids == hiddenIds) return
        val previous = hiddenIds
        hiddenIds = ids
        notifyChangedRows(previous, ids) { item, hidden -> item.id.toString() in hidden }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryItemViewHolder {
        val binding = LibraryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LibraryItemViewHolder(binding, apiClient, onItemClick, onItemLongClick)
    }

    override fun onBindViewHolder(holder: LibraryItemViewHolder, position: Int) {
        holder.bind(getItem(position), downloadedIds, hiddenIds)
    }

    class LibraryItemViewHolder(
        private val binding: LibraryItemBinding,
        private val apiClient: ApiClient,
        private val onItemClick: (LibraryItem) -> Unit,
        private val onItemLongClick: ((LibraryItem) -> Boolean)?,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LibraryItem, downloadedIds: Set<String>, hiddenIds: Set<String>) {
            binding.textViewTitle.text = item.displayName
            binding.textViewDescription.text = item.description

            val runtimeTicks = item.runTimeTicks
            val progress = item.progressPercent
            if (runtimeTicks != null) {
                val runtimeText = formatRuntime(runtimeTicks)
                binding.textViewRuntime.text = if (progress != null && progress > 0f) {
                    // Total runtime alone reads as "how much you've listened to" at a glance;
                    // spell out the percentage explicitly so it isn't mistaken for elapsed time.
                    itemView.context.getString(R.string.runtime_with_progress, runtimeText, progress.roundToInt())
                } else {
                    runtimeText
                }
                binding.textViewRuntime.visibility = android.view.View.VISIBLE
            } else {
                binding.textViewRuntime.visibility = android.view.View.GONE
            }

            if (progress != null && progress > 0f) {
                binding.progressBarPlayed.progress = progress.toInt().coerceIn(0, PROGRESS_MAX_PERCENT)
                binding.progressBarPlayed.visibility = android.view.View.VISIBLE
            } else {
                binding.progressBarPlayed.visibility = android.view.View.GONE
            }

            val imageItemId = item.imageItemId
            val thumbnailUrl = if (imageItemId != null) getThumbnailUrl(itemView.context, imageItemId) else null
            val fallbackIcon = if (imageItemId != null) R.drawable.ic_audiobook_white_64 else R.drawable.ic_folder_white_24dp
            binding.imageViewThumbnail.load(thumbnailUrl) {
                fallback(fallbackIcon)
                error(fallbackIcon)
            }

            val isDownloaded = item.isDownloaded(downloadedIds)
            binding.iconDownloaded.visibility = if (isDownloaded) android.view.View.VISIBLE else android.view.View.GONE

            // Only reachable when "Show Hidden Items" is on — dim it so it reads as hidden-but-visible.
            val isHidden = item.id.toString() in hiddenIds
            binding.iconHidden.visibility = if (isHidden) android.view.View.VISIBLE else android.view.View.GONE
            itemView.alpha = if (isHidden) HIDDEN_ITEM_ALPHA else 1f

            itemView.setOnClickListener { onItemClick(item) }
            if (onItemLongClick != null) {
                itemView.setOnLongClickListener { onItemLongClick.invoke(item) }
            }
        }

        private fun getThumbnailUrl(context: Context, itemId: UUID): String? {
            val size = context.resources.getDimensionPixelSize(R.dimen.movie_thumbnail_list_size)
            return apiClient.imageApi.getItemImageUrl(
                itemId = itemId,
                imageType = ImageType.PRIMARY,
                maxWidth = size,
                maxHeight = size,
            )
        }
    }

    private class LibraryItemDiffCallback : DiffUtil.ItemCallback<LibraryItem>() {
        override fun areItemsTheSame(oldItem: LibraryItem, newItem: LibraryItem): Boolean {
            return oldItem.id == newItem.id
        }

        // trackIds is a Kotlin List, which compares by content; lint only flags it because List is an interface
        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: LibraryItem, newItem: LibraryItem): Boolean {
            return when {
                oldItem is LibraryItem.AudiobookBundle && newItem is LibraryItem.AudiobookBundle ->
                    oldItem.trackIds == newItem.trackIds &&
                        oldItem.displayName == newItem.displayName &&
                        oldItem.progressPercent == newItem.progressPercent
                oldItem is LibraryItem.Album && newItem is LibraryItem.Album -> oldItem.dto == newItem.dto
                oldItem is LibraryItem.AudioFile && newItem is LibraryItem.AudioFile -> oldItem.dto == newItem.dto
                oldItem is LibraryItem.BrowseFolder && newItem is LibraryItem.BrowseFolder -> oldItem.dto == newItem.dto
                else -> false
            }
        }
    }
}

internal fun LibraryItem.isDownloaded(downloadedIds: Set<String>): Boolean = when (this) {
    is LibraryItem.AudiobookBundle -> trackIds.any { it.toString() in downloadedIds }
    is LibraryItem.AudioFile -> id.toString() in downloadedIds
    else -> false
}

internal fun formatRuntime(ticks: Long): String {
    val totalSeconds = ticks / TICKS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}

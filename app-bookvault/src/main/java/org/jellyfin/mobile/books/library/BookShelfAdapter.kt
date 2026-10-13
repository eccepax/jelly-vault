package org.jellyfin.mobile.books.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.fallback
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.databinding.ItemBookShelfBinding
import org.jellyfin.mobile.common.ui.content.ImageProvider
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

class BookShelfAdapter(
    private val onBookClick: (BaseItemDto) -> Unit,
    private val onBookLongClick: (BaseItemDto) -> Unit,
) : ListAdapter<BaseItemDto, BookShelfAdapter.ViewHolder>(DIFF_CALLBACK) {

    var downloadedIds: Set<String> = emptySet()
        private set

    fun updateDownloadedIds(ids: Set<String>) {
        downloadedIds = ids
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemBookShelfBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(book: BaseItemDto) {
            binding.textTitle.text = book.name
            val imageUri = book.id?.let { id ->
                ImageProvider.buildItemUri(id, ImageType.PRIMARY, book.imageTags?.get(ImageType.PRIMARY))
            }
            binding.imageCover.load(imageUri) {
                fallback(R.drawable.ic_book_placeholder)
                error(R.drawable.ic_book_placeholder)
            }
            val isDownloaded = book.id?.toString() in downloadedIds
            binding.iconDownloaded.visibility = if (isDownloaded) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onBookClick(book) }
            binding.root.setOnLongClickListener {
                onBookLongClick(book)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBookShelfBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BaseItemDto>() {
            override fun areItemsTheSame(old: BaseItemDto, new: BaseItemDto) = old.id == new.id
            override fun areContentsTheSame(old: BaseItemDto, new: BaseItemDto) = old == new
        }
    }
}

package com.socialdownloader.ui.download

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.socialdownloader.R
import com.socialdownloader.data.model.DownloadItem
import com.socialdownloader.data.model.DownloadStatus
import com.socialdownloader.databinding.ItemDownloadBinding

class DownloadItemAdapter(
    private val onCancelClick: (DownloadItem) -> Unit,
    private val onRetryClick: (DownloadItem) -> Unit,
    private val onDeleteClick: (DownloadItem) -> Unit,
    private val onItemClick: (DownloadItem) -> Unit
) : ListAdapter<DownloadItem, DownloadItemAdapter.DownloadViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val binding = ItemDownloadBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DownloadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DownloadViewHolder(
        private val binding: ItemDownloadBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DownloadItem) {
            binding.apply {
                tvTitle.text = item.title
                tvPlatform.text = "${item.platform.displayName} • ${item.quality} • ${item.format.uppercase()}"

                Glide.with(root.context)
                    .load(item.thumbnailUrl)
                    .placeholder(R.drawable.placeholder_thumbnail)
                    .error(R.drawable.placeholder_thumbnail)
                    .centerCrop()
                    .into(ivThumbnail)

                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        progressBar.visibility = View.VISIBLE
                        progressBar.isIndeterminate = false
                        progressBar.progress = item.progress
                        tvStatus.text = "${item.progress}% • ${item.downloadedFormatted} / ${item.fileSizeFormatted}"
                        tvStatus.setTextColor(root.context.getColor(R.color.blue_500))
                        btnAction.visibility = View.VISIBLE
                        btnAction.text = "Cancel"
                        btnAction.setOnClickListener { onCancelClick(item) }
                        btnDelete.visibility = View.GONE
                        ivStatusIcon.visibility = View.GONE
                    }

                    DownloadStatus.PENDING, DownloadStatus.FETCHING_INFO -> {
                        progressBar.visibility = View.VISIBLE
                        progressBar.isIndeterminate = true
                        tvStatus.text = if (item.status == DownloadStatus.FETCHING_INFO)
                            "Fetching info..." else "Queued"
                        tvStatus.setTextColor(root.context.getColor(R.color.text_secondary))
                        btnAction.visibility = View.VISIBLE
                        btnAction.text = "Cancel"
                        btnAction.setOnClickListener { onCancelClick(item) }
                        btnDelete.visibility = View.GONE
                        ivStatusIcon.visibility = View.GONE
                    }

                    DownloadStatus.COMPLETED -> {
                        progressBar.visibility = View.GONE
                        tvStatus.text = "✓ Completed • ${item.fileSizeFormatted}"
                        tvStatus.setTextColor(root.context.getColor(R.color.green_500))
                        btnAction.visibility = View.GONE
                        btnDelete.visibility = View.VISIBLE
                        btnDelete.setOnClickListener { onDeleteClick(item) }
                        ivStatusIcon.visibility = View.VISIBLE
                        ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
                        root.setOnClickListener { onItemClick(item) }
                    }

                    DownloadStatus.FAILED -> {
                        progressBar.visibility = View.GONE
                        tvStatus.text = "Failed: ${item.errorMessage.take(50)}"
                        tvStatus.setTextColor(root.context.getColor(R.color.red_500))
                        btnAction.visibility = View.VISIBLE
                        btnAction.text = "Retry"
                        btnAction.setOnClickListener { onRetryClick(item) }
                        btnDelete.visibility = View.VISIBLE
                        btnDelete.setOnClickListener { onDeleteClick(item) }
                        ivStatusIcon.visibility = View.VISIBLE
                        ivStatusIcon.setImageResource(R.drawable.ic_error)
                    }

                    DownloadStatus.CANCELLED -> {
                        progressBar.visibility = View.GONE
                        tvStatus.text = "Cancelled"
                        tvStatus.setTextColor(root.context.getColor(R.color.text_secondary))
                        btnAction.visibility = View.VISIBLE
                        btnAction.text = "Retry"
                        btnAction.setOnClickListener { onRetryClick(item) }
                        btnDelete.visibility = View.VISIBLE
                        btnDelete.setOnClickListener { onDeleteClick(item) }
                        ivStatusIcon.visibility = View.GONE
                    }

                    DownloadStatus.PAUSED -> {
                        progressBar.visibility = View.VISIBLE
                        progressBar.isIndeterminate = false
                        progressBar.progress = item.progress
                        tvStatus.text = "Paused at ${item.progress}%"
                        tvStatus.setTextColor(root.context.getColor(R.color.orange_500))
                        btnAction.visibility = View.VISIBLE
                        btnAction.text = "Resume"
                        btnAction.setOnClickListener { onRetryClick(item) }
                        btnDelete.visibility = View.VISIBLE
                        btnDelete.setOnClickListener { onDeleteClick(item) }
                        ivStatusIcon.visibility = View.GONE
                    }
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DownloadItem>() {
        override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem) =
            oldItem == newItem
    }
}

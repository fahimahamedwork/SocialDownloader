package com.socialdownloader.ui.library

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.socialdownloader.R
import com.socialdownloader.data.model.DownloadItem
import com.socialdownloader.data.model.DownloadStatus
import com.socialdownloader.data.repository.DownloadRepository
import com.socialdownloader.databinding.FragmentLibraryBinding
import com.socialdownloader.databinding.ItemLibraryBinding
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: DownloadRepository
) : ViewModel() {

    val completedDownloads: StateFlow<List<DownloadItem>> =
        repository.getCompletedDownloads()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _totalStats = MutableStateFlow(LibraryStats())
    val totalStats: StateFlow<LibraryStats> = _totalStats.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            val totalBytes = repository.getTotalDownloadedBytes()
            val count = repository.getCompletedCount()
            _totalStats.value = LibraryStats(count, totalBytes)
        }
    }

    fun deleteItem(item: DownloadItem, deleteFile: Boolean) {
        viewModelScope.launch {
            if (deleteFile && item.filePath.isNotEmpty()) {
                File(item.filePath).delete()
            }
            repository.deleteDownload(item.id)
            loadStats()
        }
    }

    fun shareItem(item: DownloadItem): File? {
        return if (item.filePath.isNotEmpty()) File(item.filePath) else null
    }
}

data class LibraryStats(
    val fileCount: Int = 0,
    val totalBytes: Long = 0
) {
    val totalFormatted: String get() = when {
        totalBytes < 1024 * 1024 -> "${totalBytes / 1024} KB"
        totalBytes < 1024 * 1024 * 1024 -> "${totalBytes / (1024 * 1024)} MB"
        else -> String.format("%.1f GB", totalBytes.toFloat() / (1024 * 1024 * 1024))
    }
}

// ─── Adapter ──────────────────────────────────────────────────────────────────

class LibraryAdapter(
    private val onItemClick: (DownloadItem) -> Unit,
    private val onShareClick: (DownloadItem) -> Unit,
    private val onDeleteClick: (DownloadItem) -> Unit
) : RecyclerView.Adapter<LibraryAdapter.LibraryViewHolder>() {

    private val items = mutableListOf<DownloadItem>()

    fun submitList(newItems: List<DownloadItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryViewHolder {
        val binding = ItemLibraryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LibraryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LibraryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class LibraryViewHolder(
        private val binding: ItemLibraryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DownloadItem) {
            binding.tvTitle.text = item.title
            binding.tvInfo.text = "${item.quality} • ${item.fileSizeFormatted}"
            binding.tvPlatform.text = item.platform.displayName

            Glide.with(binding.root.context)
                .load(item.thumbnailUrl)
                .placeholder(R.drawable.placeholder_thumbnail)
                .centerCrop()
                .into(binding.ivThumbnail)

            if (item.isAudioOnly) {
                binding.ivAudioBadge.visibility = View.VISIBLE
            } else {
                binding.ivAudioBadge.visibility = View.GONE
            }

            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnShare.setOnClickListener { onShareClick(item) }
            binding.btnDelete.setOnClickListener { onDeleteClick(item) }
        }
    }
}

// ─── Fragment ─────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LibraryViewModel by viewModels()
    private lateinit var adapter: LibraryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = LibraryAdapter(
            onItemClick = { item -> playFile(item) },
            onShareClick = { item -> shareFile(item) },
            onDeleteClick = { item -> confirmDelete(item) }
        )

        binding.rvLibrary.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = this@LibraryFragment.adapter
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.completedDownloads.collect { items ->
                    adapter.submitList(items)

                    if (items.isEmpty()) {
                        binding.layoutEmpty.visibility = View.VISIBLE
                        binding.rvLibrary.visibility = View.GONE
                    } else {
                        binding.layoutEmpty.visibility = View.GONE
                        binding.rvLibrary.visibility = View.VISIBLE
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.totalStats.collect { stats ->
                    binding.tvStats.text = "${stats.fileCount} files • ${stats.totalFormatted}"
                }
            }
        }
    }

    private fun playFile(item: DownloadItem) {
        if (item.filePath.isEmpty()) {
            Snackbar.make(binding.root, "File not found", Snackbar.LENGTH_SHORT).show()
            return
        }
        val file = File(item.filePath)
        if (!file.exists()) {
            Snackbar.make(binding.root, "File no longer exists", Snackbar.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (item.isAudioOnly) "audio/*" else "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Play with"))
    }

    private fun shareFile(item: DownloadItem) {
        val file = viewModel.shareItem(item) ?: return
        if (!file.exists()) {
            Snackbar.make(binding.root, "File not found", Snackbar.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (item.isAudioOnly) "audio/*" else "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }

    private fun confirmDelete(item: DownloadItem) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete")
            .setMessage("Delete \"${item.title.take(40)}\"?")
            .setPositiveButton("Delete File") { _, _ ->
                viewModel.deleteItem(item, deleteFile = true)
            }
            .setNeutralButton("Remove Entry") { _, _ ->
                viewModel.deleteItem(item, deleteFile = false)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

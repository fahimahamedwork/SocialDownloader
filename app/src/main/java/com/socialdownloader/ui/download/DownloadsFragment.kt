package com.socialdownloader.ui.download

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.socialdownloader.R
import com.socialdownloader.data.model.DownloadItem
import com.socialdownloader.databinding.FragmentDownloadsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DownloadsViewModel by viewModels()

    private lateinit var activeAdapter: DownloadItemAdapter
    private lateinit var completedAdapter: DownloadItemAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupTabs()
        observeViewModel()
    }

    private fun setupRecyclerViews() {
        activeAdapter = DownloadItemAdapter(
            onCancelClick = { item -> viewModel.cancelDownload(item) },
            onRetryClick = { item -> viewModel.retryDownload(item) },
            onDeleteClick = { item -> confirmDelete(item) },
            onItemClick = { item -> openFile(item) }
        )

        completedAdapter = DownloadItemAdapter(
            onCancelClick = {},
            onRetryClick = {},
            onDeleteClick = { item -> confirmDelete(item) },
            onItemClick = { item -> openFile(item) }
        )

        binding.rvActiveDownloads.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = activeAdapter
        }

        binding.rvCompletedDownloads.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = completedAdapter
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showActiveDownloads()
                    1 -> showCompletedDownloads()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showActiveDownloads() {
        binding.rvActiveDownloads.visibility = View.VISIBLE
        binding.rvCompletedDownloads.visibility = View.GONE
    }

    private fun showCompletedDownloads() {
        binding.rvActiveDownloads.visibility = View.GONE
        binding.rvCompletedDownloads.visibility = View.VISIBLE
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activeDownloads.collect { downloads ->
                activeAdapter.submitList(downloads)
                updateEmptyState(downloads.isEmpty(), isActive = true)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.completedDownloads.collect { downloads ->
                completedAdapter.submitList(downloads)
                updateEmptyState(downloads.isEmpty(), isActive = false)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.snackbarMessage.collect { message ->
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean, isActive: Boolean) {
        if (isActive && binding.tabLayout.selectedTabPosition == 0) {
            binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.tvEmptyTitle.text = "No active downloads"
            binding.tvEmptySubtitle.text = "Downloads you start will appear here"
        } else if (!isActive && binding.tabLayout.selectedTabPosition == 1) {
            binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.tvEmptyTitle.text = "No completed downloads"
            binding.tvEmptySubtitle.text = "Completed downloads will appear here"
        }
    }

    private fun confirmDelete(item: DownloadItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Download")
            .setMessage("Do you want to delete the downloaded file as well?")
            .setNegativeButton("Keep File") { _, _ ->
                viewModel.deleteDownload(item, deleteFile = false)
            }
            .setPositiveButton("Delete File") { _, _ ->
                viewModel.deleteDownload(item, deleteFile = true)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun openFile(item: DownloadItem) {
        if (item.filePath.isEmpty()) return
        val file = java.io.File(item.filePath)
        if (!file.exists()) {
            Snackbar.make(binding.root, "File not found", Snackbar.LENGTH_SHORT).show()
            return
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )

        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (item.isAudioOnly) "audio/*" else "video/*")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(android.content.Intent.createChooser(intent, "Open with"))
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_downloads, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_completed -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Clear Completed")
                    .setMessage("Remove all completed downloads from the list?")
                    .setPositiveButton("Clear") { _, _ ->
                        viewModel.clearAllCompleted()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

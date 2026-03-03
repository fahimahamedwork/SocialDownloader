package com.socialdownloader.ui.home

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.socialdownloader.R
import com.socialdownloader.data.model.Platform
import com.socialdownloader.data.model.VideoFormat
import com.socialdownloader.data.model.VideoInfo
import com.socialdownloader.databinding.FragmentHomeBinding
import com.socialdownloader.databinding.BottomSheetVideoInfoBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        // Paste button
        binding.btnPaste.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (text.isNotEmpty()) {
                binding.etUrl.setText(text)
                viewModel.onUrlChanged(text)
            }
        }

        // Clear button
        binding.btnClear.setOnClickListener {
            viewModel.clearUrl()
            binding.etUrl.text?.clear()
        }

        // Download button
        binding.btnAnalyze.setOnClickListener {
            val url = binding.etUrl.text?.toString()?.trim() ?: ""
            viewModel.analyzeUrl(url)
        }

        // URL text change listener
        binding.etUrl.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val url = s?.toString() ?: ""
                viewModel.onUrlChanged(url)
                binding.btnClear.visibility = if (url.isEmpty()) View.GONE else View.VISIBLE

                // Show platform chip
                updatePlatformIndicator(url)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Platform chips for supported platforms
        setupPlatformChips()
    }

    private fun setupPlatformChips() {
        val platforms = listOf(
            Platform.YOUTUBE, Platform.INSTAGRAM, Platform.TIKTOK,
            Platform.FACEBOOK, Platform.TWITTER, Platform.VIMEO,
            Platform.DAILYMOTION, Platform.REDDIT
        )
        platforms.forEach { platform ->
            val chip = Chip(requireContext()).apply {
                text = platform.displayName
                isCheckable = false
                setChipBackgroundColorResource(R.color.chip_background)
            }
            binding.chipGroupPlatforms.addView(chip)
        }
    }

    private fun updatePlatformIndicator(url: String) {
        if (url.isNotEmpty() && url.startsWith("http")) {
            val platform = Platform.fromUrl(url)
            binding.platformIndicator.visibility = View.VISIBLE
            binding.tvPlatformName.text = platform.displayName
        } else {
            binding.platformIndicator.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUiState(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pastedUrl.collect { url ->
                if (binding.etUrl.text?.toString() != url) {
                    binding.etUrl.setText(url)
                }
            }
        }
    }

    private fun updateUiState(state: HomeUiState) {
        when (state) {
            is HomeUiState.Idle -> {
                binding.progressBar.visibility = View.GONE
                binding.btnAnalyze.isEnabled = true
                binding.btnAnalyze.text = getString(R.string.analyze_url)
                binding.tvError.visibility = View.GONE
            }

            is HomeUiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.btnAnalyze.isEnabled = false
                binding.btnAnalyze.text = state.message
                binding.tvError.visibility = View.GONE
            }

            is HomeUiState.VideoInfoLoaded -> {
                binding.progressBar.visibility = View.GONE
                binding.btnAnalyze.isEnabled = true
                binding.btnAnalyze.text = getString(R.string.analyze_url)
                binding.tvError.visibility = View.GONE
                showVideoInfoSheet(state.videoInfo)
            }

            is HomeUiState.DownloadStarted -> {
                binding.progressBar.visibility = View.GONE
                binding.btnAnalyze.isEnabled = true
                binding.btnAnalyze.text = getString(R.string.analyze_url)
                Snackbar.make(binding.root, "Download started!", Snackbar.LENGTH_LONG)
                    .setAction("View") {
                        findNavController().navigate(R.id.downloadsFragment)
                    }
                    .show()
                viewModel.resetState()
            }

            is HomeUiState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.btnAnalyze.isEnabled = true
                binding.btnAnalyze.text = getString(R.string.analyze_url)
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = state.message

                val shake = AnimationUtils.loadAnimation(requireContext(), R.anim.shake)
                binding.inputContainer.startAnimation(shake)
            }
        }
    }

    private fun showVideoInfoSheet(videoInfo: VideoInfo) {
        val sheetBinding = BottomSheetVideoInfoBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetTheme)
        dialog.setContentView(sheetBinding.root)

        // Populate video info
        sheetBinding.tvTitle.text = videoInfo.title
        sheetBinding.tvPlatform.text = videoInfo.platform.displayName
        sheetBinding.tvDuration.text = videoInfo.durationFormatted
        sheetBinding.tvUploader.text = videoInfo.uploader
        sheetBinding.tvViews.text = "${videoInfo.viewCountFormatted} views"

        Glide.with(this)
            .load(videoInfo.thumbnailUrl)
            .placeholder(R.drawable.placeholder_thumbnail)
            .into(sheetBinding.ivThumbnail)

        // Populate format options
        videoInfo.formats.forEach { format ->
            val chip = Chip(requireContext()).apply {
                text = if (format.isAudioOnly) {
                    "🎵 Audio (MP3)"
                } else {
                    "🎬 ${format.quality} ${format.fileSizeFormatted}"
                }
                isCheckable = true
                isChecked = false
                tag = format
            }
            sheetBinding.chipGroupFormats.addView(chip)
        }

        // Select first format by default
        if (sheetBinding.chipGroupFormats.childCount > 0) {
            (sheetBinding.chipGroupFormats.getChildAt(0) as? Chip)?.isChecked = true
        }

        sheetBinding.btnDownload.setOnClickListener {
            val selectedChip = sheetBinding.chipGroupFormats
                .checkedChipId
                .let { id -> sheetBinding.chipGroupFormats.findViewById<Chip>(id) }

            val selectedFormat = selectedChip?.tag as? VideoFormat
                ?: videoInfo.formats.firstOrNull()

            if (selectedFormat != null) {
                viewModel.startDownload(videoInfo, selectedFormat)
                dialog.dismiss()
            } else {
                Snackbar.make(binding.root, "Please select a format", Snackbar.LENGTH_SHORT).show()
            }
        }

        sheetBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

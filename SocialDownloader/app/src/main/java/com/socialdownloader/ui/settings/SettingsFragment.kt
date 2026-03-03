package com.socialdownloader.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.socialdownloader.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Settings Model ───────────────────────────────────────────────────────────

data class AppSettings(
    val defaultQuality: String = "720p",
    val downloadPath: String = "Movies/SocialDownloader",
    val maxConcurrentDownloads: Int = 3,
    val wifiOnlyDownload: Boolean = false,
    val autoScanMedia: Boolean = true,
    val showNotifications: Boolean = true,
    val theme: String = "system", // system, light, dark
    val autoDetectClipboard: Boolean = true
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<androidx.datastore.preferences.core.Preferences>
) : ViewModel() {

    private val KEY_DEFAULT_QUALITY = stringPreferencesKey("default_quality")
    private val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
    private val KEY_AUTO_SCAN = booleanPreferencesKey("auto_scan")
    private val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications")
    private val KEY_THEME = stringPreferencesKey("theme")
    private val KEY_AUTO_CLIPBOARD = booleanPreferencesKey("auto_clipboard")
    private val KEY_MAX_CONCURRENT = intPreferencesKey("max_concurrent")

    val settings: StateFlow<AppSettings> = dataStore.data
        .map { prefs ->
            AppSettings(
                defaultQuality = prefs[KEY_DEFAULT_QUALITY] ?: "720p",
                wifiOnlyDownload = prefs[KEY_WIFI_ONLY] ?: false,
                autoScanMedia = prefs[KEY_AUTO_SCAN] ?: true,
                showNotifications = prefs[KEY_NOTIFICATIONS] ?: true,
                theme = prefs[KEY_THEME] ?: "system",
                autoDetectClipboard = prefs[KEY_AUTO_CLIPBOARD] ?: true,
                maxConcurrentDownloads = prefs[KEY_MAX_CONCURRENT] ?: 3
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun setDefaultQuality(quality: String) = updatePref { it[KEY_DEFAULT_QUALITY] = quality }
    fun setWifiOnly(enabled: Boolean) = updatePref { it[KEY_WIFI_ONLY] = enabled }
    fun setAutoScan(enabled: Boolean) = updatePref { it[KEY_AUTO_SCAN] = enabled }
    fun setNotifications(enabled: Boolean) = updatePref { it[KEY_NOTIFICATIONS] = enabled }
    fun setTheme(theme: String) = updatePref { it[KEY_THEME] = theme }
    fun setAutoClipboard(enabled: Boolean) = updatePref { it[KEY_AUTO_CLIPBOARD] = enabled }
    fun setMaxConcurrent(count: Int) = updatePref { it[KEY_MAX_CONCURRENT] = count }

    private fun updatePref(block: (MutablePreferences) -> Unit) {
        viewModelScope.launch {
            dataStore.edit { block(it) }
        }
    }
}

// ─── Fragment ─────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeSettings()
    }

    private fun setupUI() {
        binding.switchWifiOnly.setOnCheckedChangeListener { _, checked ->
            viewModel.setWifiOnly(checked)
        }

        binding.switchAutoScan.setOnCheckedChangeListener { _, checked ->
            viewModel.setAutoScan(checked)
        }

        binding.switchNotifications.setOnCheckedChangeListener { _, checked ->
            viewModel.setNotifications(checked)
        }

        binding.switchAutoClipboard.setOnCheckedChangeListener { _, checked ->
            viewModel.setAutoClipboard(checked)
        }

        // Quality selector
        binding.qualityGroup.setOnCheckedChangeListener { group, checkedId ->
            val quality = when (checkedId) {
                binding.radio1080p.id -> "1080p"
                binding.radio720p.id -> "720p"
                binding.radio480p.id -> "480p"
                binding.radio360p.id -> "360p"
                else -> "720p"
            }
            viewModel.setDefaultQuality(quality)
        }

        // Theme selector
        binding.themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                binding.radioLight.id -> "light"
                binding.radioDark.id -> "dark"
                else -> "system"
            }
            viewModel.setTheme(theme)
        }

        // App version
        binding.tvAppVersion.text = "Version ${
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
        }"
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.settings.collect { settings ->
                binding.switchWifiOnly.isChecked = settings.wifiOnlyDownload
                binding.switchAutoScan.isChecked = settings.autoScanMedia
                binding.switchNotifications.isChecked = settings.showNotifications
                binding.switchAutoClipboard.isChecked = settings.autoDetectClipboard

                when (settings.defaultQuality) {
                    "1080p" -> binding.radio1080p.isChecked = true
                    "720p" -> binding.radio720p.isChecked = true
                    "480p" -> binding.radio480p.isChecked = true
                    "360p" -> binding.radio360p.isChecked = true
                }

                when (settings.theme) {
                    "light" -> binding.radioLight.isChecked = true
                    "dark" -> binding.radioDark.isChecked = true
                    else -> binding.radioSystem.isChecked = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

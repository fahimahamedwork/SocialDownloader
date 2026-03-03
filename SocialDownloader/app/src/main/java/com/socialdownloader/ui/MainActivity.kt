package com.socialdownloader.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.socialdownloader.R
import com.socialdownloader.databinding.ActivityMainBinding
import com.socialdownloader.ui.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        // Badge for active downloads
        lifecycleScope.launch {
            homeViewModel.activeDownloadCount.collect { count ->
                val badge = binding.bottomNavigation.getOrCreateBadge(R.id.downloadsFragment)
                if (count > 0) {
                    badge.isVisible = true
                    badge.number = count
                } else {
                    badge.isVisible = false
                }
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        // Handle shared URLs from other apps
        if (intent.action == Intent.ACTION_SEND &&
            intent.type == "text/plain"
        ) {
            val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedUrl.isNullOrBlank()) {
                // Navigate to home and pass the URL
                navController.navigate(R.id.homeFragment)
                homeViewModel.onUrlChanged(sharedUrl)
                homeViewModel.analyzeUrl(sharedUrl)
            }
        }

        // Handle deep link from notification
        if (intent.hasExtra("tab") && intent.getStringExtra("tab") == "library") {
            navController.navigate(R.id.libraryFragment)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}

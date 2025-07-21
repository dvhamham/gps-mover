package com.hamham.gpsmover.view

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.hamham.gpsmover.R
import com.hamham.gpsmover.databinding.ActivityMapBinding
import com.hamham.gpsmover.modules.DbManager
import com.hamham.gpsmover.modules.DeviceManager
import com.hamham.gpsmover.modules.RulesManager
import com.hamham.gpsmover.modules.UpdateManager
import com.hamham.gpsmover.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MapActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMapBinding.inflate(layoutInflater) }
    val viewModel by viewModels<MainViewModel>()
    private var currentPage = "map"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure the system window for modern edge-to-edge design
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        // --- Startup sequence ---
        // 1. Check for mandatory or optional app updates
        UpdateManager.checkUpdate(this) {
            // 2. Validate and migrate the database if needed
            DbManager.dbMigrate(this)
            // 3. Update device information in the database
            DeviceManager.updateDeviceInfo(this)
            // 4. Check for a global app ban (kill switch)
            RulesManager.applicationDisabled(this) {}
        }
        // 5. Check if the user is logged in
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        // 6. Load the map page as the default Fragment on first launch
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.map_fragment_container, MapFragment(), "MapFragment")
                .commit()
        }
        // 7. Initialize the bottom navigation bar
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        DeviceManager.checkBanStatus(this)
        RulesManager.applicationDisabled(this) {}
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { menuItem ->
            bottomNavigation.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            when (menuItem.itemId) {
                R.id.navigation_map -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.map_fragment_container, MapFragment(), "MapFragment")
                        .commit()
                    true
                }
                R.id.navigation_favorites -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.map_fragment_container, FavoritesFragment(), "FavoritesFragment")
                        .commit()
                    true
                }
                R.id.navigation_settings -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.map_fragment_container, SettingsFragment(), "SettingsFragment")
                        .commit()
                    true
                }
                else -> false
            }
        }
        when (currentPage) {
            "map" -> bottomNavigation.selectedItemId = R.id.navigation_map
            "favorites" -> bottomNavigation.selectedItemId = R.id.navigation_favorites
            "settings" -> bottomNavigation.selectedItemId = R.id.navigation_settings
            else -> bottomNavigation.selectedItemId = R.id.navigation_map
        }
    }
}
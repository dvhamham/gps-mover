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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        // Startup sequence
        DbManager.checkAndMigrateDatabase(this)
        DeviceManager.updateDeviceInfo(this)
        UpdateManager.checkUpdate(this) {
            RulesManager.applicationDisabled(this) {
                // Ban check will be handled in onResume
            }
        }
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.map_fragment_container, MapFragment(), "MapFragment")
                .commit()
        }
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        DeviceManager.checkBanStatus(this)
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { menuItem ->
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
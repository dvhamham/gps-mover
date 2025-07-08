package com.hamham.gpsmover.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.hamham.gpsmover.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_map -> {
                    loadFragment(MapFragment())
                    true
                }
                R.id.menu_favourites -> {
                    loadFragment(FavouritesFragment())
                    true
                }
                R.id.menu_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
        // Load default fragment
        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.menu_map
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
} 
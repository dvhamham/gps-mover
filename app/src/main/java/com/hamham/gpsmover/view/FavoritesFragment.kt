package com.hamham.gpsmover.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.hamham.gpsmover.R
import com.hamham.gpsmover.feature.locations.presentation.ui.LocationsView
import com.hamham.gpsmover.feature.locations.presentation.viewmodel.LocationCompatibilityViewModel
import com.hamham.gpsmover.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FavoritesFragment : Fragment() {
    private val mainViewModel: MainViewModel by activityViewModels()
    private val locationsViewModel: LocationCompatibilityViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_favorites, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val locationsView = view as LocationsView
        
        // Set up the new locations view with compatibility ViewModel
        locationsView.setCompatibilityViewModel(locationsViewModel, lifecycleScope)
        
        locationsView.setOnLocationClick { location ->
            // Switch to map page
            requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.navigation_map
            // Pass coordinates to main ViewModel
            mainViewModel.moveToLocation(location.latitude, location.longitude)
            mainViewModel.update(true, location.latitude, location.longitude)
        }
    }
} 
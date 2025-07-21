package com.hamham.gpsmover.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.hamham.gpsmover.R
import com.hamham.gpsmover.favorites.FavoritesPage
import com.hamham.gpsmover.viewmodel.MainViewModel
import kotlinx.coroutines.MainScope

class FavoritesFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private val mainScope = MainScope()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_favorites, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val favoritesPage = view as FavoritesPage
        favoritesPage.setViewModel(viewModel, mainScope)
        favoritesPage.setOnFavoriteClick { favourite ->
            // Switch to map page
            requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.navigation_map
            // Pass coordinates to ViewModel
            viewModel.moveToLocation(favourite.lat ?: 0.0, favourite.lng ?: 0.0)
            viewModel.update(true, favourite.lat ?: 0.0, favourite.lng ?: 0.0)
        }
    }
} 
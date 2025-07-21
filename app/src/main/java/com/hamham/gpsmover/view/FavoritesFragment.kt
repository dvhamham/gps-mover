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
            // يمكنك هنا التنقل للخريطة أو تنفيذ أي منطق عند الضغط على مفضلة
        }
    }
} 
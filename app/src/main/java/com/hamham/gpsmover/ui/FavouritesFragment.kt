package com.hamham.gpsmover.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hamham.gpsmover.R
import com.hamham.gpsmover.adapter.FavListAdapter
import com.hamham.gpsmover.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavouritesFragment : Fragment() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var favListAdapter: FavListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fav, container, false)
        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.favorites_list)
        favListAdapter = FavListAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = favListAdapter

        favListAdapter.onItemClick = {
            // Optionally, handle click (e.g., show details or move map)
        }
        favListAdapter.onItemDelete = {
            viewModel.deleteFavourite(it)
        }

        lifecycleScope.launch {
            viewModel.allFavList.collectLatest {
                favListAdapter.submitList(it)
            }
        }
        viewModel.doGetUserDetails()
        return view
    }
} 
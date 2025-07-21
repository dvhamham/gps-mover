package com.hamham.gpsmover.favorites

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hamham.gpsmover.R
import com.hamham.gpsmover.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// A fully self-contained custom view to display and manage the user's favorite locations.
class FavoritesPage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var viewModel: MainViewModel? = null
    private var onFavoriteClick: ((Favourite) -> Unit)? = null
    private val favListAdapter = FavListAdapter()
    private var favoritesJob: Job? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.favorites_page, this, true)
        setupRecyclerView()
    }

    // Set up the RecyclerView, adapter, and item touch helper for drag-and-drop.
    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = favListAdapter

        val itemTouchHelper = ItemTouchHelper(createItemTouchHelperCallback(favListAdapter))
        itemTouchHelper.attachToRecyclerView(recyclerView)

        favListAdapter.onItemClick = { favourite ->
            onFavoriteClick?.invoke(favourite)
        }
        favListAdapter.onItemDelete = { favourite ->
            viewModel?.deleteFavourite(favourite)
        }
        favListAdapter.onItemMove = {
            val updatedFavorites = favListAdapter.getItems().mapIndexed { index, favourite ->
                favourite.copy(order = index)
            }
            viewModel?.updateFavouritesOrder(updatedFavorites)
        }
    }

    // Must be called from the parent to provide the ViewModel.
    fun setViewModel(viewModel: MainViewModel, scope: CoroutineScope) {
        this.viewModel = viewModel
        observeFavorites(scope)
    }

    // Sets the listener for when a favorite item is clicked.
    fun setOnFavoriteClick(listener: (Favourite) -> Unit) {
        this.onFavoriteClick = listener
    }

    // Observes the list of favorites from the ViewModel and updates the UI.
    private fun observeFavorites(scope: CoroutineScope) {
        favoritesJob?.cancel() // Cancel any previous observer
        favoritesJob = scope.launch {
            viewModel?.allFavList?.collectLatest { favorites ->
                updateFavoritesList(favorites)
            }
        }
    }

    // Shows or hides the empty state view based on the favorites list.
    private fun updateFavoritesList(favorites: List<Favourite>) {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val emptyCard = findViewById<View>(R.id.emptyCard)
        if (favorites.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyCard.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyCard.visibility = View.GONE
            favListAdapter.setItems(favorites)
        }
    }
}

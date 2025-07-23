package com.hamham.gpsmover.feature.locations.presentation.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hamham.gpsmover.R
import com.hamham.gpsmover.feature.locations.domain.model.Location
import com.hamham.gpsmover.feature.locations.presentation.adapter.LocationTouchHelperCallback
import com.hamham.gpsmover.feature.locations.presentation.adapter.LocationsAdapter
import com.hamham.gpsmover.feature.locations.presentation.viewmodel.LocationsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Modern self-contained custom view for displaying and managing locations.
 * This replaces the old FavoritesPage with clean architecture principles.
 */
class LocationsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var viewModel: LocationsViewModel? = null
    private var onLocationClick: ((Location) -> Unit)? = null
    
    private val locationsAdapter = LocationsAdapter()
    private var locationsJob: Job? = null
    private var eventsJob: Job? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.favorites_page, this, true)
        setupRecyclerView()
    }

    /**
     * Sets up the RecyclerView with adapter and touch helper
     */
    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = locationsAdapter
        
        // Basic RecyclerView optimizations for drag and drop
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(10)

        // Setup drag and drop functionality with enhanced callback
        val touchHelper = ItemTouchHelper(LocationTouchHelperCallback(locationsAdapter))
        touchHelper.attachToRecyclerView(recyclerView)

        // Setup adapter callbacks - will be overridden by specific ViewModel setup
        locationsAdapter.onLocationClick = { location ->
            onLocationClick?.invoke(location)
        }
    }

    /**
     * Sets the ViewModel and starts observing data
     * Must be called from the parent to provide the ViewModel
     */
    fun setViewModel(viewModel: LocationsViewModel, scope: CoroutineScope) {
        this.viewModel = viewModel
        setupAdapterCallbacks(viewModel)
        observeData(scope)
    }

    /**
     * Sets the compatibility ViewModel for backward compatibility
     */
    fun setCompatibilityViewModel(compatibilityViewModel: com.hamham.gpsmover.feature.locations.presentation.viewmodel.LocationCompatibilityViewModel, scope: CoroutineScope) {
        // For compatibility, we'll observe the locations from the compatibility ViewModel
        setupCompatibilityAdapterCallbacks(compatibilityViewModel)
        observeCompatibilityData(compatibilityViewModel, scope)
    }

    /**
     * Sets up adapter callbacks for regular ViewModel
     */
    private fun setupAdapterCallbacks(viewModel: LocationsViewModel) {
        locationsAdapter.onLocationDelete = { location ->
            viewModel.deleteLocation(location)
        }
        
        locationsAdapter.onLocationMove = {
            val updatedLocations = locationsAdapter.getCurrentLocationsList()
                .mapIndexed { index, location ->
                    location.withOrder(index)
                }
            viewModel.reorderLocations(updatedLocations)
        }
    }

    /**
     * Sets up adapter callbacks for compatibility ViewModel
     */
    private fun setupCompatibilityAdapterCallbacks(compatibilityViewModel: com.hamham.gpsmover.feature.locations.presentation.viewmodel.LocationCompatibilityViewModel) {
        locationsAdapter.onLocationDelete = { location ->
            compatibilityViewModel.deleteLocation(location)
        }
        
        locationsAdapter.onLocationMove = {
            val updatedLocations = locationsAdapter.getCurrentLocationsList()
                .mapIndexed { index, location ->
                    location.withOrder(index)
                }
            compatibilityViewModel.reorderLocations(updatedLocations)
        }
    }

    /**
     * Sets the listener for location item clicks
     */
    fun setOnLocationClick(listener: (Location) -> Unit) {
        this.onLocationClick = listener
    }

    /**
     * Observes locations and UI state from the ViewModel
     */
    private fun observeData(scope: CoroutineScope) {
        // Cancel any previous observers
        locationsJob?.cancel()
        eventsJob?.cancel()
        
        // Observe locations list
        locationsJob = scope.launch {
            viewModel?.locations?.collectLatest { locations ->
                updateLocationsList(locations)
            }
        }
        
        // Observe events (for error handling, etc.)
        eventsJob = scope.launch {
            viewModel?.events?.collectLatest { event ->
                handleEvent(event)
            }
        }
    }

    /**
     * Observes data from compatibility ViewModel (backward compatibility)
     */
    private fun observeCompatibilityData(compatibilityViewModel: com.hamham.gpsmover.feature.locations.presentation.viewmodel.LocationCompatibilityViewModel, scope: CoroutineScope) {
        // Cancel any previous observers
        locationsJob?.cancel()
        eventsJob?.cancel()
        
        // Observe locations list from compatibility ViewModel
        locationsJob = scope.launch {
            compatibilityViewModel.allLocationsList.collectLatest { locations ->
                updateLocationsList(locations)
            }
        }
    }

    /**
     * Updates the locations list and manages empty state visibility
     */
    private fun updateLocationsList(locations: List<Location>) {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val emptyCard = findViewById<View>(R.id.emptyCard)
        
        if (locations.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyCard.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyCard.visibility = View.GONE
            locationsAdapter.submitList(locations)
        }
    }

    /**
     * Handles ViewModel events
     */
    private fun handleEvent(event: com.hamham.gpsmover.feature.locations.presentation.viewmodel.LocationsEvent) {
        when (event) {
            is com.hamham.gpsmover.feature.locations.presentation.viewmodel.LocationsEvent.Error -> {
                // Could show a snackbar or toast here
                // For now, just log the error
                android.util.Log.e("LocationsView", "Error: ${event.message}")
            }
            is com.hamham.gpsmover.feature.locations.presentation.viewmodel.LocationsEvent.LocationDeleted,
            is com.hamham.gpsmover.feature.locations.presentation.viewmodel.LocationsEvent.LocationSaved,
            is com.hamham.gpsmover.feature.locations.presentation.viewmodel.LocationsEvent.LocationsReordered -> {
                // Success events - no action needed as the UI will update via flow
            }
        }
    }

    /**
     * Cleanup method to cancel ongoing operations
     */
    fun cleanup() {
        locationsJob?.cancel()
        eventsJob?.cancel()
    }
}

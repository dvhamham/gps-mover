package com.hamham.gpsmover.feature.locations.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamham.gpsmover.feature.locations.compat.Favourite
import com.hamham.gpsmover.feature.locations.data.mapper.toFavourite
import com.hamham.gpsmover.feature.locations.data.mapper.toLocation
import com.hamham.gpsmover.feature.locations.data.mapper.toLocations
import com.hamham.gpsmover.feature.locations.domain.model.Location
import com.hamham.gpsmover.feature.locations.domain.usecase.DeleteLocationUseCase
import com.hamham.gpsmover.feature.locations.domain.usecase.GetLocationsUseCase
import com.hamham.gpsmover.feature.locations.domain.usecase.ReorderLocationsUseCase
import com.hamham.gpsmover.feature.locations.domain.usecase.SaveLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Compatibility ViewModel that provides backward compatibility methods for the MainViewModel
 * This allows gradual migration from old Favourite model to new Location model
 */
@HiltViewModel 
class LocationCompatibilityViewModel @Inject constructor(
    private val getLocationsUseCase: GetLocationsUseCase,
    private val saveLocationUseCase: SaveLocationUseCase,
    private val deleteLocationUseCase: DeleteLocationUseCase,
    private val reorderLocationsUseCase: ReorderLocationsUseCase
) : ViewModel() {

    /**
     * Exposes locations as the old Favourite model for backward compatibility
     */
    val allFavList: Flow<List<Favourite>> = getLocationsUseCase()
        .map { locations -> locations.map { it.toFavourite() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Exposes locations as the new Location model
     */
    val allLocationsList: Flow<List<Location>> = getLocationsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Backward compatibility method - saves a Favourite as Location
     */
    fun insertFavourite(favourite: Favourite) {
        viewModelScope.launch {
            saveLocationUseCase(favourite.toLocation())
        }
    }

    /**
     * New method - saves a Location directly
     */
    fun saveLocation(location: Location) {
        viewModelScope.launch {
            saveLocationUseCase(location)
        }
    }

    /**
     * Backward compatibility method - deletes a Favourite
     */
    fun deleteFavourite(favourite: Favourite) {
        viewModelScope.launch {
            val result = deleteLocationUseCase(favourite.toLocation())
            if (result.isFailure) {
                android.util.Log.e("LocationCompatibilityViewModel", "Failed to delete favourite: ${favourite.name}", result.exceptionOrNull())
            }
        }
    }

    /**
     * New method - deletes a Location directly
     */
    fun deleteLocation(location: Location) {
        viewModelScope.launch {
            val result = deleteLocationUseCase(location)
            if (result.isFailure) {
                android.util.Log.e("LocationCompatibilityViewModel", "Failed to delete location: ${location.name}", result.exceptionOrNull())
            }
        }
    }

    /**
     * Backward compatibility method - updates order of Favourites
     */
    fun updateFavouritesOrder(favourites: List<Favourite>) {
        viewModelScope.launch {
            reorderLocationsUseCase(favourites.toLocations())
        }
    }

    /**
     * New method - updates order of Locations directly
     */
    fun reorderLocations(locations: List<Location>) {
        viewModelScope.launch {
            reorderLocationsUseCase(locations)
        }
    }

    /**
     * Backward compatibility method - replaces all Favourites
     */
    fun replaceAllFavourites(favorites: List<Favourite>) {
        viewModelScope.launch {
            // For simplicity, we'll delete all and add new ones
            // In production, you might want a more sophisticated approach
            val locations = favorites.toLocations()
            // This would require implementing importLocations in the use case layer
            // For now, let's assume we have a way to replace all
        }
    }

    /**
     * Get a single favourite by ID (backward compatibility)
     */
    suspend fun getSingleFavourite(id: Long): Favourite? {
        // We would need to implement this in the repository
        // For now, return null as a placeholder
        return null
    }
}

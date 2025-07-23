package com.hamham.gpsmover.feature.locations.compat

import android.content.Context
import com.hamham.gpsmover.feature.locations.compat.Favourite
import com.hamham.gpsmover.feature.locations.data.mapper.toFavourite
import com.hamham.gpsmover.feature.locations.data.mapper.toLocation
import com.hamham.gpsmover.feature.locations.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legacy FavouriteRepository for backward compatibility
 * @deprecated Use LocationRepository from feature.locations.domain.repository instead
 */
@Deprecated("Use LocationRepository from feature.locations.domain.repository instead")
@Singleton
class FavouriteRepository @Inject constructor(
    private val locationRepository: LocationRepository
) {
    
    val getAllFavourites: Flow<List<Favourite>> = locationRepository.getAllLocations()
        .map { locations -> locations.map { it.toFavourite() } }
    
    fun initializeListener(context: Context) {
        // No-op - the new system handles this automatically
    }
    
    suspend fun addNewFavourite(context: Context, favourite: Favourite): Long {
        locationRepository.saveLocation(favourite.toLocation())
        return favourite.id
    }
    
    suspend fun deleteFavourite(context: Context, favourite: Favourite) {
        locationRepository.deleteLocation(favourite.toLocation())
    }
    
    suspend fun updateFavouritesOrder(context: Context, favourites: List<Favourite>) {
        locationRepository.updateLocationsOrder(favourites.map { it.toLocation() })
    }
    
    suspend fun insertAllFavourites(context: Context, favorites: List<Favourite>) {
        locationRepository.importLocations(favorites.map { it.toLocation() })
    }
    
    suspend fun replaceAllFavourites(context: Context, favorites: List<Favourite>) {
        locationRepository.importLocations(favorites.map { it.toLocation() })
    }
    
    fun stopListener() {
        // No-op - the new system handles this automatically
    }
}

package com.hamham.gpsmover.feature.locations.domain.repository

import com.hamham.gpsmover.feature.locations.domain.model.Location
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for location operations.
 * Defines the contract for data access without implementation details.
 */
interface LocationRepository {
    
    /**
     * Gets all saved locations as a flow, ordered by their order field
     */
    fun getAllLocations(): Flow<List<Location>>
    
    /**
     * Saves a new location or updates existing one
     */
    suspend fun saveLocation(location: Location)
    
    /**
     * Deletes a location by its entity
     */
    suspend fun deleteLocation(location: Location)
    
    /**
     * Updates the order of multiple locations
     */
    suspend fun updateLocationsOrder(locations: List<Location>)
    
    /**
     * Gets a location by its ID
     */
    suspend fun getLocationById(id: String): Location?
    
    /**
     * Imports multiple locations, replacing existing ones
     */
    suspend fun importLocations(locations: List<Location>)
    
    /**
     * Exports all locations for backup
     */
    suspend fun exportLocations(): List<Location>
}

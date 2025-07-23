package com.hamham.gpsmover.feature.locations.presentation.util

import com.hamham.gpsmover.feature.locations.domain.model.Coordinates
import com.hamham.gpsmover.feature.locations.domain.model.Location

/**
 * Utility class for creating locations with the new coordinate system
 */
object LocationFactory {
    
    /**
     * Creates a new location with coordinates
     */
    fun createLocation(
        name: String,
        latitude: Double,
        longitude: Double,
        order: Int = 0
    ): Location {
        return Location(
            id = System.currentTimeMillis().toString(),
            name = name,
            coordinates = Coordinates(latitude, longitude),
            order = order,
            createdAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Creates a location from coordinate string "lat,lng"
     */
    fun createLocationFromString(
        name: String,
        coordinatesString: String,
        order: Int = 0
    ): Location? {
        val coordinates = Coordinates.fromString(coordinatesString)
        return if (coordinates != null) {
            Location(
                id = System.currentTimeMillis().toString(),
                name = name,
                coordinates = coordinates,
                order = order,
                createdAt = System.currentTimeMillis()
            )
        } else null
    }
    
    /**
     * Creates a location with example coordinates (Casa Blanca, Morocco)
     */
    fun createExampleLocation(name: String): Location {
        return createLocation(
            name = name,
            latitude = 33.5731,
            longitude = -7.5898
        )
    }
}

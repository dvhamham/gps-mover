package com.hamham.gpsmover.feature.locations.data.mapper

import com.hamham.gpsmover.feature.locations.compat.Favourite
import com.hamham.gpsmover.feature.locations.domain.model.Coordinates
import com.hamham.gpsmover.feature.locations.domain.model.Location

/**
 * Extension functions to convert between old Favourite model and new Location model
 * These are useful for migration and backward compatibility
 */

/**
 * Converts old Favourite model to new Location domain model
 */
fun Favourite.toLocation(): Location {
    return Location(
        id = this.id.toString(),
        name = this.name,
        coordinates = Coordinates(this.lat, this.lng),
        order = this.order,
        createdAt = this.id // Use the old ID (timestamp) as created time
    )
}

/**
 * Converts new Location domain model to old Favourite model
 */
fun Location.toFavourite(): Favourite {
    return Favourite(
        id = this.id.toLongOrNull() ?: this.createdAt,
        name = this.name,
        lat = this.coordinates.latitude,
        lng = this.coordinates.longitude,
        order = this.order
    )
}

/**
 * Converts list of old Favourite models to new Location models  
 */
fun List<Favourite>.toLocations(): List<Location> {
    return this.map { it.toLocation() }
}

/**
 * Converts list of new Location models to old Favourite models
 */
fun List<Location>.toFavourites(): List<Favourite> {
    return this.map { it.toFavourite() }
}

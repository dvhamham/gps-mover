package com.hamham.gpsmover.feature.locations.domain.model

/**
 * Domain model representing a saved location.
 * This is the core business entity independent of external frameworks.
 */
data class Location(
    val id: String = "",
    val name: String,
    val coordinates: Coordinates,
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Validates if the location has valid coordinates
     */
    fun isValidLocation(): Boolean {
        return coordinates.isValid() && name.isNotBlank()
    }
    
    /**
     * Returns formatted coordinate string for display
     */
    fun getFormattedCoordinates(): String {
        return coordinates.getFormattedString()
    }
    
    /**
     * Gets latitude value (convenience method for backward compatibility)
     */
    val latitude: Double get() = coordinates.latitude
    
    /**
     * Gets longitude value (convenience method for backward compatibility)
     */
    val longitude: Double get() = coordinates.longitude
    
    /**
     * Creates a copy with new order
     */
    fun withOrder(newOrder: Int): Location {
        return copy(order = newOrder)
    }
}

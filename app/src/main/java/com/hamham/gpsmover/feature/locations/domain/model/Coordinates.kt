package com.hamham.gpsmover.feature.locations.domain.model

/**
 * Data class representing geographical coordinates
 */
data class Coordinates(
    val latitude: Double,
    val longitude: Double
) {
    /**
     * Validates if coordinates are within valid ranges
     */
    fun isValid(): Boolean {
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
    
    /**
     * Returns formatted coordinate string for display
     */
    fun getFormattedString(): String {
        return "${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"
    }
    
    /**
     * Converts to Firestore format - as a single string "lat,lng"
     */
    fun toFirestoreString(): String {
        return "${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"
    }
    
    /**
     * Converts to Firestore map format (for backward compatibility)
     */
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "latitude" to latitude,
            "longitude" to longitude
        )
    }
    
    companion object {
        /**
         * Creates coordinates from Firestore string format "lat, lng"
         */
        fun fromFirestoreString(coordString: String): Coordinates? {
            return try {
                val parts = coordString.split(",").map { it.trim().toDouble() }
                if (parts.size == 2) {
                    Coordinates(parts[0], parts[1])
                } else null
            } catch (e: Exception) {
                null
            }
        }
        
        /**
         * Creates coordinates from Firestore map (for backward compatibility)
         */
        fun fromFirestoreMap(map: Map<String, Any>): Coordinates {
            return Coordinates(
                latitude = (map["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (map["longitude"] as? Number)?.toDouble() ?: 0.0
            )
        }
        
        /**
         * Creates coordinates from string format "lat,lng"
         */
        fun fromString(coordString: String): Coordinates? {
            return fromFirestoreString(coordString)
        }
    }
}

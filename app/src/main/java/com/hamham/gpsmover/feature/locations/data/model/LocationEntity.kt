package com.hamham.gpsmover.feature.locations.data.model

import com.hamham.gpsmover.feature.locations.domain.model.Coordinates
import com.hamham.gpsmover.feature.locations.domain.model.Location

/**
 * Data model for location entity used for Firebase Firestore mapping.
 * This handles the conversion between domain model and external data format.
 */
data class LocationEntity(
    val id: String = "",
    val name: String = "",
    val coordinates: Coordinates = Coordinates(0.0, 0.0),
    val order: Int = 0,
    val createdAt: Long = 0L
) {
    /**
     * Converts this entity to Firestore map format with coordinates as string
     */
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "name" to name,
            "coordinates" to coordinates.toFirestoreString(), // Save as string
            "order" to order,
            "createdAt" to createdAt
        )
    }
    
    /**
     * Converts this entity to domain model
     */
    fun toDomainModel(): Location {
        return Location(
            id = id,
            name = name,
            coordinates = coordinates, // Already a Coordinates object
            order = order,
            createdAt = createdAt
        )
    }
    
    companion object {
        /**
         * Creates entity from Firestore map - supports both string and map coordinates format
         */
        fun fromFirestoreMap(map: Map<String, Any>): LocationEntity {
            val coordinatesData = map["coordinates"]
            val coordinates = when (coordinatesData) {
                is String -> {
                    // New format: coordinates as string "lat, lng"
                    Coordinates.fromFirestoreString(coordinatesData) ?: Coordinates(0.0, 0.0)
                }
                is Map<*, *> -> {
                    // Old format: coordinates as map {latitude: x, longitude: y}
                    @Suppress("UNCHECKED_CAST")
                    Coordinates.fromFirestoreMap(coordinatesData as Map<String, Any>)
                }
                else -> Coordinates(0.0, 0.0)
            }
            
            return LocationEntity(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "",
                coordinates = coordinates,
                order = (map["order"] as? Number)?.toInt() ?: 0,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
            )
        }
        
        /**
         * Creates entity from domain model
         */
        fun fromDomainModel(location: Location): LocationEntity {
            return LocationEntity(
                id = location.id,
                name = location.name,
                coordinates = location.coordinates,
                order = location.order,
                createdAt = location.createdAt
            )
        }
    }
}

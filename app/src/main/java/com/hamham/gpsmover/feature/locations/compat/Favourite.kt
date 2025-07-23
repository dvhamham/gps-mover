package com.hamham.gpsmover.feature.locations.compat

import com.hamham.gpsmover.modules.DeviceKeys

/**
 * Legacy Favourite data class for backward compatibility
 * @deprecated Use com.hamham.gpsmover.feature.locations.domain.model.Location instead
 */
@Deprecated("Use Location from feature.locations.domain.model instead")
data class Favourite(
    val id: Long = System.currentTimeMillis(),
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val order: Int = 0
) {
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            DeviceKeys.FavouritesKeys.FavouriteItem.ID to id,
            DeviceKeys.FavouritesKeys.FavouriteItem.NAME to name,
            DeviceKeys.FavouritesKeys.FavouriteItem.LAT to lat,
            DeviceKeys.FavouritesKeys.FavouriteItem.LNG to lng,
            DeviceKeys.FavouritesKeys.FavouriteItem.ORDER to order
        )
    }
    
    companion object {
        fun fromFirestoreMap(map: Map<String, Any>): Favourite {
            return Favourite(
                id = (map[DeviceKeys.FavouritesKeys.FavouriteItem.ID] as? Number)?.toLong() ?: System.currentTimeMillis(),
                name = map[DeviceKeys.FavouritesKeys.FavouriteItem.NAME] as? String ?: "",
                lat = (map[DeviceKeys.FavouritesKeys.FavouriteItem.LAT] as? Number)?.toDouble() ?: 0.0,
                lng = (map[DeviceKeys.FavouritesKeys.FavouriteItem.LNG] as? Number)?.toDouble() ?: 0.0,
                order = (map[DeviceKeys.FavouritesKeys.FavouriteItem.ORDER] as? Number)?.toInt() ?: 0
            )
        }
    }
}

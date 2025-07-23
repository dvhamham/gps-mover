package com.hamham.gpsmover.favorites

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldValue
import com.hamham.gpsmover.modules.Collections
import com.hamham.gpsmover.modules.DeviceKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Data class representing a Favourite item (matches Firestore structure)
 */
data class Favourite(
    val id: Long = System.currentTimeMillis(),  // Unique timestamp-based ID
    val name: String = "",                      // Human-readable name/address
    val lat: Double = 0.0,                      // Latitude coordinate
    val lng: Double = 0.0,                      // Longitude coordinate
    val order: Int = 0                          // Custom order index for sorting
) {
    // Convert to Firestore Map format
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
        // Convert from Firestore Map format
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

/**
 * Repository for Favourite data operations using Firestore
 * Replaces the old Room Database system with Firestore integration
 */
class FavouriteRepository @Inject constructor() {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val _favouritesFlow = MutableStateFlow<List<Favourite>>(emptyList())
    private var listenerRegistration: ListenerRegistration? = null
    
    companion object {
        private const val TAG = "FavouriteRepository"
    }
    
    // Flow of all favourites to be observed (Live updates)
    val getAllFavourites: Flow<List<Favourite>> get() = _favouritesFlow.asStateFlow()
    
    /**
     * Initialize real-time listening to favourites for the current device
     */
    fun initializeListener(context: Context) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceRef = firestore.collection(Collections.DEVICES).document(androidId)
        
        Log.i(TAG, "🎧 Starting to listen to favourites for device: $androidId")
        
        // Remove existing listener if any
        listenerRegistration?.remove()
        
        listenerRegistration = deviceRef.addSnapshotListener { document, exception ->
            if (exception != null) {
                Log.e(TAG, "❌ Error listening to favourites", exception)
                return@addSnapshotListener
            }
            
            if (document != null && document.exists()) {
                val favouritesData = document.get(DeviceKeys.FAVOURITES) as? Map<String, Any>
                val favouritesList = favouritesData?.get(DeviceKeys.FavouritesKeys.LIST) as? List<Map<String, Any>> ?: emptyList()
                
                // Convert to Favourite objects and sort by order
                val favourites = favouritesList.map { Favourite.fromFirestoreMap(it) }
                    .sortedWith(compareBy<Favourite> { it.order }.thenByDescending { it.id })
                
                Log.i(TAG, "🔄 Favourites updated: ${favourites.size} items")
                _favouritesFlow.value = favourites
            } else {
                Log.i(TAG, "📋 No favourites document found")
                _favouritesFlow.value = emptyList()
            }
        }
    }
    
    /**
     * Stop listening to favourites updates
     */
    fun stopListener() {
        listenerRegistration?.remove()
        listenerRegistration = null
        Log.i(TAG, "🔇 Stopped listening to favourites")
    }
    
    /**
     * Add a new favourite to Firestore
     */
    suspend fun addNewFavourite(context: Context, favourite: Favourite): Long {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceRef = firestore.collection(Collections.DEVICES).document(androidId)
        
        Log.i(TAG, "📍 Adding favourite: ${favourite.name}")
        
        return try {
            deviceRef.update(
                "${DeviceKeys.FAVOURITES}.${DeviceKeys.FavouritesKeys.LIST}", 
                FieldValue.arrayUnion(favourite.toFirestoreMap())
            ).let { 
                Log.i(TAG, "✅ Successfully added favourite: ${favourite.name}")
                favourite.id 
            }
        } catch (exception: Exception) {
            Log.e(TAG, "❌ Failed to add favourite: ${favourite.name}", exception)
            throw exception
        }
    }
    
    /**
     * Delete a single favourite from Firestore
     */
    suspend fun deleteFavourite(context: Context, favourite: Favourite) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceRef = firestore.collection(Collections.DEVICES).document(androidId)
        
        Log.i(TAG, "🗑️ Deleting favourite: ${favourite.name}")
        
        try {
            deviceRef.update(
                "${DeviceKeys.FAVOURITES}.${DeviceKeys.FavouritesKeys.LIST}", 
                FieldValue.arrayRemove(favourite.toFirestoreMap())
            )
            Log.i(TAG, "✅ Successfully deleted favourite: ${favourite.name}")
        } catch (exception: Exception) {
            Log.e(TAG, "❌ Failed to delete favourite: ${favourite.name}", exception)
            throw exception
        }
    }
    
    /**
     * Get a single favourite by ID (from current flow state)
     */
    fun getSingleFavourite(id: Long): Favourite? {
        return _favouritesFlow.value.find { it.id == id }
    }
    
    /**
     * Update the order of all favourites (replace entire list)
     */
    suspend fun updateFavouritesOrder(context: Context, favourites: List<Favourite>) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceRef = firestore.collection(Collections.DEVICES).document(androidId)
        
        Log.i(TAG, "📋 Updating favourites order for ${favourites.size} items")
        
        try {
            val favouritesData = mapOf(
                DeviceKeys.FavouritesKeys.LIST to favourites.map { it.toFirestoreMap() }
            )
            
            deviceRef.update(DeviceKeys.FAVOURITES, favouritesData)
            Log.i(TAG, "✅ Successfully updated favourites order")
        } catch (exception: Exception) {
            Log.e(TAG, "❌ Failed to update favourites order", exception)
            throw exception
        }
    }
    
    /**
     * Delete all favourites from Firestore
     */
    suspend fun deleteAllFavourites(context: Context) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceRef = firestore.collection(Collections.DEVICES).document(androidId)
        
        Log.i(TAG, "🗑️ Deleting all favourites")
        
        try {
            val emptyFavouritesData = mapOf(
                DeviceKeys.FavouritesKeys.LIST to emptyList<Map<String, Any>>()
            )
            
            deviceRef.update(DeviceKeys.FAVOURITES, emptyFavouritesData)
            Log.i(TAG, "✅ Successfully deleted all favourites")
        } catch (exception: Exception) {
            Log.e(TAG, "❌ Failed to delete all favourites", exception)
            throw exception
        }
    }
    
    /**
     * Insert a batch of favourites (replace existing)
     */
    suspend fun insertAllFavourites(context: Context, favorites: List<Favourite>) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceRef = firestore.collection(Collections.DEVICES).document(androidId)
        
        Log.i(TAG, "📥 Inserting ${favorites.size} favourites")
        
        try {
            val favouritesData = mapOf(
                DeviceKeys.FavouritesKeys.LIST to favorites.map { it.toFirestoreMap() }
            )
            
            deviceRef.update(DeviceKeys.FAVOURITES, favouritesData)
            Log.i(TAG, "✅ Successfully inserted all favourites")
        } catch (exception: Exception) {
            Log.e(TAG, "❌ Failed to insert favourites", exception)
            throw exception
        }
    }
    
    /**
     * Replace all favourites with a new list
     */
    suspend fun replaceAllFavourites(context: Context, favorites: List<Favourite>) {
        // Same as insertAllFavourites since we're replacing the entire list
        insertAllFavourites(context, favorites)
    }
}

// Removed Room Database classes as they're no longer needed
// All data is now stored in Firestore under devices/{androidId}/favourites/list

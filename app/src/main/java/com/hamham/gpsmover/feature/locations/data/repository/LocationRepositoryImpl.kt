package com.hamham.gpsmover.feature.locations.data.repository

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.hamham.gpsmover.feature.locations.data.model.LocationEntity
import com.hamham.gpsmover.feature.locations.domain.model.Location
import com.hamham.gpsmover.feature.locations.domain.repository.LocationRepository
import com.hamham.gpsmover.modules.Collections
import com.hamham.gpsmover.modules.DeviceKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Firestore implementation of LocationRepository
 */
@Singleton
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val migrationService: com.hamham.gpsmover.feature.locations.data.migration.LocationDataMigrationService
) : LocationRepository {

    private val tag = "LocationRepository"
    private var listenerRegistration: ListenerRegistration? = null
    private var migrationCompleted = false

    override fun getAllLocations(): Flow<List<Location>> = callbackFlow {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceRef = firestore.collection(Collections.DEVICES).document(androidId)
        
        Log.i(tag, "🔄 Starting to listen for location updates")
        
        // Run migration if needed
        if (!migrationCompleted) {
            try {
                if (migrationService.isMigrationNeeded(context)) {
                    Log.i(tag, "🔄 Running data migration...")
                    migrationService.migrateLocationData(context)
                }
                migrationCompleted = true
            } catch (e: Exception) {
                Log.e(tag, "❌ Migration failed, continuing with current data", e)
            }
        }
        
        listenerRegistration = deviceRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(tag, "❌ Error listening to locations", error)
                return@addSnapshotListener
            }
            
            if (snapshot?.exists() == true) {
                try {
                    val data = snapshot.data
                    val favouritesData = data?.get(DeviceKeys.FAVOURITES) as? Map<*, *>
                    val favouritesList = favouritesData?.get(DeviceKeys.FavouritesKeys.LIST) as? List<*>
                    
                    val locations = favouritesList?.mapNotNull { item ->
                        (item as? Map<*, *>)?.let { map ->
                            val stringMap = map.mapKeys { it.key.toString() }
                                .mapValues { it.value ?: "" }
                            
                            try {
                                LocationEntity.fromFirestoreMap(stringMap).toDomainModel()
                            } catch (e: Exception) {
                                Log.w(tag, "⚠️ Failed to parse location: ${stringMap["name"]}", e)
                                null
                            }
                        }
                    }?.sortedBy { it.order } ?: emptyList()
                    
                    Log.i(tag, "📍 Received ${locations.size} locations from Firestore")
                    trySend(locations)
                } catch (e: Exception) {
                    Log.e(tag, "❌ Error parsing locations data", e)
                    trySend(emptyList())
                }
            } else {
                Log.i(tag, "📭 No device document found, sending empty list")
                trySend(emptyList())
            }
        }
        
        awaitClose {
            Log.i(tag, "🔇 Stopping location listener")
            listenerRegistration?.remove()
            listenerRegistration = null
        }
    }

    override suspend fun saveLocation(location: Location) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceRef = firestore.collection(Collections.DEVICES).document(androidId)
        val locationEntity = LocationEntity.fromDomainModel(location)
        
        Log.i(tag, "💾 Saving location: ${location.name}")
        
        try {
            deviceRef.update(
                "${DeviceKeys.FAVOURITES}.${DeviceKeys.FavouritesKeys.LIST}",
                FieldValue.arrayUnion(locationEntity.toFirestoreMap())
            ).await()
            
            Log.i(tag, "✅ Successfully saved location: ${location.name}")
        } catch (exception: Exception) {
            Log.e(tag, "❌ Failed to save location: ${location.name}", exception)
            throw exception
        }
    }

    override suspend fun deleteLocation(location: Location) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceRef = firestore.collection(Collections.DEVICES).document(androidId)
        
        Log.i(tag, "🗑️ Deleting location: ${location.name} (ID: ${location.id})")
        
        try {
            // Get current locations list
            val snapshot = deviceRef.get().await()
            val data = snapshot.data
            val favouritesData = data?.get(DeviceKeys.FAVOURITES) as? Map<*, *>
            val favouritesList = favouritesData?.get(DeviceKeys.FavouritesKeys.LIST) as? MutableList<*>
            
            if (favouritesList != null) {
                // Find and remove the location by ID
                val iterator = favouritesList.iterator()
                var found = false
                while (iterator.hasNext()) {
                    val item = iterator.next() as? Map<*, *>
                    val itemId = item?.get("id")?.toString()
                    if (itemId == location.id) {
                        iterator.remove()
                        found = true
                        break
                    }
                }
                
                if (found) {
                    // Update the list in Firestore
                    val updatedFavouritesData = mapOf(
                        DeviceKeys.FavouritesKeys.LIST to favouritesList
                    )
                    deviceRef.update(DeviceKeys.FAVOURITES, updatedFavouritesData).await()
                    Log.i(tag, "✅ Successfully deleted location: ${location.name}")
                } else {
                    Log.w(tag, "⚠️ Location with ID ${location.id} not found for deletion")
                }
            } else {
                Log.w(tag, "⚠️ No favorites list found")
            }
        } catch (exception: Exception) {
            Log.e(tag, "❌ Failed to delete location: ${location.name}", exception)
            throw exception
        }
    }

    override suspend fun updateLocationsOrder(locations: List<Location>) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceRef = firestore.collection(Collections.DEVICES).document(androidId)
        
        Log.i(tag, "📋 Updating locations order for ${locations.size} items")
        
        try {
            val locationsData = mapOf(
                DeviceKeys.FavouritesKeys.LIST to locations.map { 
                    LocationEntity.fromDomainModel(it).toFirestoreMap() 
                }
            )
            
            deviceRef.update(DeviceKeys.FAVOURITES, locationsData).await()
            Log.i(tag, "✅ Successfully updated locations order")
        } catch (exception: Exception) {
            Log.e(tag, "❌ Failed to update locations order", exception)
            throw exception
        }
    }

    override suspend fun getLocationById(id: String): Location? {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceRef = firestore.collection(Collections.DEVICES).document(androidId)
        
        return try {
            val snapshot = deviceRef.get().await()
            val data = snapshot.data
            val favouritesData = data?.get(DeviceKeys.FAVOURITES) as? Map<*, *>
            val favouritesList = favouritesData?.get(DeviceKeys.FavouritesKeys.LIST) as? List<*>
            
            favouritesList?.mapNotNull { item ->
                (item as? Map<*, *>)?.let { map ->
                    val stringMap = map.mapKeys { it.key.toString() }
                        .mapValues { it.value ?: "" }
                    LocationEntity.fromFirestoreMap(stringMap).toDomainModel()
                }
            }?.find { it.id == id }
        } catch (exception: Exception) {
            Log.e(tag, "❌ Failed to get location by ID: $id", exception)
            null
        }
    }

    override suspend fun importLocations(locations: List<Location>) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceRef = firestore.collection(Collections.DEVICES).document(androidId)
        
        Log.i(tag, "📥 Importing ${locations.size} locations")
        
        try {
            val locationsData = mapOf(
                DeviceKeys.FavouritesKeys.LIST to locations.map { 
                    LocationEntity.fromDomainModel(it).toFirestoreMap() 
                }
            )
            
            deviceRef.update(DeviceKeys.FAVOURITES, locationsData).await()
            Log.i(tag, "✅ Successfully imported all locations")
        } catch (exception: Exception) {
            Log.e(tag, "❌ Failed to import locations", exception)
            throw exception
        }
    }

    override suspend fun exportLocations(): List<Location> {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceRef = firestore.collection(Collections.DEVICES).document(androidId)
        
        return try {
            val snapshot = deviceRef.get().await()
            val data = snapshot.data
            val favouritesData = data?.get(DeviceKeys.FAVOURITES) as? Map<*, *>
            val favouritesList = favouritesData?.get(DeviceKeys.FavouritesKeys.LIST) as? List<*>
            
            val locations = favouritesList?.mapNotNull { item ->
                (item as? Map<*, *>)?.let { map ->
                    val stringMap = map.mapKeys { it.key.toString() }
                        .mapValues { it.value ?: "" }
                    LocationEntity.fromFirestoreMap(stringMap).toDomainModel()
                }
            }?.sortedBy { it.order } ?: emptyList()
            
            Log.i(tag, "📤 Exported ${locations.size} locations")
            locations
        } catch (exception: Exception) {
            Log.e(tag, "❌ Failed to export locations", exception)
            throw exception
        }
    }
}

package com.hamham.gpsmover.feature.locations.data.migration

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.hamham.gpsmover.modules.Collections
import com.hamham.gpsmover.modules.DeviceKeys
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service to migrate location data from old format (lat, lng) to new format (coordinates object)
 */
@Singleton
class LocationDataMigrationService @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    
    private val tag = "LocationMigration"
    
    /**
     * Migrates location data from old format to new format
     */
    suspend fun migrateLocationData(context: Context): Boolean {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val deviceRef = firestore.collection(Collections.DEVICES).document(androidId)
            
            Log.i(tag, "🔄 Starting location data migration...")
            
            val snapshot = deviceRef.get().await()
            if (!snapshot.exists()) {
                Log.i(tag, "📭 No device document found, migration not needed")
                return true
            }
            
            val data = snapshot.data
            val favouritesData = data?.get(DeviceKeys.FAVOURITES) as? Map<*, *>
            val favouritesList = favouritesData?.get(DeviceKeys.FavouritesKeys.LIST) as? List<*>
            
            if (favouritesList.isNullOrEmpty()) {
                Log.i(tag, "📭 No favorites found, migration not needed")
                return true
            }
            
            val migratedList = mutableListOf<Map<String, Any>>()
            var migrationNeeded = false
            
            favouritesList.forEach { item ->
                val map = item as? Map<*, *> ?: return@forEach
                val stringMap = map.mapKeys { it.key.toString() }.mapValues { it.value ?: "" }
                
                // Check if old format exists (lat, lng fields)
                if (stringMap.containsKey("lat") && stringMap.containsKey("lng")) {
                    migrationNeeded = true
                    
                    val lat = (stringMap["lat"] as? Number)?.toDouble() ?: 0.0
                    val lng = (stringMap["lng"] as? Number)?.toDouble() ?: 0.0
                    
                    // Create new format with coordinates as string
                    val migratedItem = stringMap.toMutableMap().apply {
                        // Add new coordinates field as string
                        put("coordinates", "${String.format("%.6f", lat)}, ${String.format("%.6f", lng)}")
                        // Remove old fields
                        remove("lat")
                        remove("lng")
                    }
                    
                    migratedList.add(migratedItem)
                    Log.d(tag, "🔄 Migrated location: ${stringMap["name"]} from ($lat, $lng) to coordinates string")
                } else if (stringMap.containsKey("coordinates")) {
                    val coords = stringMap["coordinates"]
                    if (coords is Map<*, *>) {
                        // Convert from old object format to new string format
                        migrationNeeded = true
                        val lat = (coords["latitude"] as? Number)?.toDouble() ?: 0.0
                        val lng = (coords["longitude"] as? Number)?.toDouble() ?: 0.0
                        
                        val migratedItem = stringMap.toMutableMap().apply {
                            put("coordinates", "${String.format("%.6f", lat)}, ${String.format("%.6f", lng)}")
                        }
                        migratedList.add(migratedItem)
                        Log.d(tag, "🔄 Migrated location: ${stringMap["name"]} from coordinates object to string")
                    } else {
                        // Already in new string format
                        migratedList.add(stringMap)
                    }
                } else {
                    Log.w(tag, "⚠️ Location without coordinates found: ${stringMap["name"]}")
                    migratedList.add(stringMap)
                }
            }
            
            if (migrationNeeded) {
                // Update Firestore with migrated data
                val favouritesData = mapOf(
                    DeviceKeys.FavouritesKeys.LIST to migratedList
                )
                
                deviceRef.update(DeviceKeys.FAVOURITES, favouritesData).await()
                Log.i(tag, "✅ Successfully migrated ${migratedList.size} locations to new format")
            } else {
                Log.i(tag, "✅ All locations already in new format, no migration needed")
            }
            
            true
        } catch (exception: Exception) {
            Log.e(tag, "❌ Failed to migrate location data", exception)
            false
        }
    }
    
    /**
     * Checks if migration is needed
     */
    suspend fun isMigrationNeeded(context: Context): Boolean {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val deviceRef = firestore.collection(Collections.DEVICES).document(androidId)
            
            val snapshot = deviceRef.get().await()
            if (!snapshot.exists()) return false
            
            val data = snapshot.data
            val favouritesData = data?.get(DeviceKeys.FAVOURITES) as? Map<*, *>
            val favouritesList = favouritesData?.get(DeviceKeys.FavouritesKeys.LIST) as? List<*>
            
            favouritesList?.any { item ->
                val map = item as? Map<*, *> ?: return@any false
                val stringMap = map.mapKeys { it.key.toString() }
                // Check if old format exists (lat/lng fields or coordinates object)
                stringMap.containsKey("lat") && stringMap.containsKey("lng") ||
                        (stringMap.containsKey("coordinates") && stringMap["coordinates"] is Map<*, *>)
            } ?: false
        } catch (exception: Exception) {
            Log.e(tag, "❌ Failed to check migration status", exception)
            false
        }
    }
}

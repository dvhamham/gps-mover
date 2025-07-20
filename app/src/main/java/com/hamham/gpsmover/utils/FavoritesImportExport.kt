package com.hamham.gpsmover.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hamham.gpsmover.favorites.Favourite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FavoritesImportExport(private val context: Context) {
    
    private val gson = Gson()
    
    // Removed exportFavorites function to avoid creating multiple files
    // Export is now handled directly from MapActivity
    
    /**
     * استيراد المفضلات من ملف JSON
     */
    suspend fun importFavorites(uri: Uri): List<Favourite>? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val jsonString = inputStream?.bufferedReader().use { it?.readText() }
            Log.e("FavoritesImportExport", "JSON: $jsonString")
            if (jsonString.isNullOrBlank()) {
                Log.e("FavoritesImportExport", "JSON file is empty or null")
                return@withContext null
            }
            val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
            val rawList: List<Map<String, Any?>>? = try {
                Gson().fromJson(jsonString, type)
            } catch (e: Exception) {
                Log.e("FavoritesImportExport", "JSON is not a valid array of objects", e)
                return@withContext null
            }
            if (rawList == null) {
                Log.e("FavoritesImportExport", "Parsed list is null")
                return@withContext null
            }
            val validFavorites = rawList.mapNotNull { map ->
                try {
                    val id = (map["id"] as? Number)?.toLong() ?: return@mapNotNull null
                    val address = map["address"] as? String ?: ""
                    val lat = (map["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
                    val lng = (map["lng"] as? Number)?.toDouble() ?: return@mapNotNull null
                    val order = (map["order"] as? Number)?.toInt() ?: 0
                    Favourite(id = id, address = address, lat = lat, lng = lng, order = order)
                } catch (e: Exception) {
                    Log.e("FavoritesImportExport", "Invalid item in JSON: $map", e)
                    null
                }
            }
            Log.d("FavoritesImportExport", "Imported ${validFavorites.size} valid favorites from file")
            if (validFavorites.isEmpty()) {
                Log.e("FavoritesImportExport", "No valid favorites found in file")
                return@withContext null
            }
            // حفظ المفضلة المستوردة مباشرة في Firestore
            val user = FirebaseAuth.getInstance().currentUser
            val email = user?.email
            if (email != null) {
                FirebaseFirestore.getInstance().collection("favorites").document(email)
                    .set(mapOf("list" to validFavorites))
            }
            validFavorites
        } catch (e: Exception) {
            Log.e("FavoritesImportExport", "Error importing favorites", e)
            null
        }
    }
    
    // Removed getExportFilePath function as it is no longer needed
} 
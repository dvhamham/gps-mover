package com.hamham.gpsmover.modules

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.regex.Pattern

object SearchManager {
    sealed class SearchResult {
        data class Success(val latLng: LatLng) : SearchResult()
        data class Error(val message: String) : SearchResult()
    }

    /**
     * Suspend function to search for a location by coordinates or address.
     * Returns Success(LatLng) or Error(message).
     */
    suspend fun search(context: Context, query: String): SearchResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext SearchResult.Error("Search query is empty")
        }
        // Try to parse as coordinates first
        val matcher = Pattern.compile("[-+]?\\d{1,3}(?:[.]\\d+)?, *[-+]?\\d{1,3}(?:[.]\\d+)?").matcher(query)
        if (matcher.matches()) {
            return@withContext try {
                val parts = query.split(",").map { it.trim() }
                val lat = parts[0].toDouble()
                val lng = parts[1].toDouble()
                if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                    SearchResult.Success(LatLng(lat, lng))
                } else {
                    SearchResult.Error("Invalid coordinates range")
                }
            } catch (e: Exception) {
                SearchResult.Error("Invalid coordinates format")
            }
        }
        // If not coordinates, try address search
        try {
            val geocoder = Geocoder(context)
            val addresses: List<Address>? = geocoder.getFromLocationName(query, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                SearchResult.Success(LatLng(address.latitude, address.longitude))
            } else {
                SearchResult.Error("Address not found")
            }
        } catch (io: IOException) {
            SearchResult.Error("No internet connection")
        } catch (e: Exception) {
            SearchResult.Error("Search failed: ${e.localizedMessage}")
        }
    }

    /**
     * UI-friendly function: launches search and calls the appropriate callback on the main thread.
     */
    fun performSearch(
        context: Context,
        query: String,
        onSuccess: (LatLng) -> Unit,
        onError: (String) -> Unit
    ) {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
            when (val result = search(context, query)) {
                is SearchResult.Success -> onSuccess(result.latLng)
                is SearchResult.Error -> onError(result.message)
            }
        }
    }
} 
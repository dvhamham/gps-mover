package com.hamham.gpsmover

import android.content.Context
import android.util.Log
import com.hamham.gpsmover.modules.CollectionsManager
import com.hamham.gpsmover.modules.UpdateManager

/**
 * Centralized app initialization manager.
 * Prevents duplicate initialization calls and manages app startup sequence.
 */
object AppInitializer {
    private const val TAG = "AppInitializer"
    private var isInitialized = false
    private var isInitializing = false
    
    /**
     * Initializes app data after successful authentication.
     * This method is safe to call multiple times - it will only run once.
     * 
     * @param context Application context
     * @param onComplete Callback when initialization is complete
     */
    fun initializeAppData(context: Context, onComplete: (() -> Unit)? = null) {
        // Prevent multiple initializations
        if (isInitialized) {
            onComplete?.invoke()
            return
        }
        
        if (isInitializing) {
            return
        }
        
        isInitializing = true
        
        try {
            // Database migration should run first BEFORE any other operations
            // Device information will be updated automatically after database sync
            CollectionsManager.initializeCollections(context)
            
            // Check comprehensive app status (killall and ban status)
            CollectionsManager.checkAppStatus(context) {
                // If app is disabled, the method will handle shutdown
                // Otherwise continue without update check (handled separately in MapActivity)
                isInitialized = true
                isInitializing = false
                onComplete?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting app initialization", e)
            isInitializing = false
            onComplete?.invoke()
        }
    }
    
    /**
     * Resets initialization state. 
     * Use this when user logs out or when you need to force re-initialization.
     */
    fun resetInitialization() {
        isInitialized = false
        isInitializing = false
    }
    
    /**
     * Check if app has been initialized
     */
    fun isAppInitialized(): Boolean = isInitialized
}

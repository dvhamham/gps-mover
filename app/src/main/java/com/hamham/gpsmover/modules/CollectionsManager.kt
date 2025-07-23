package com.hamham.gpsmover.modules

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Professional Collections Manager for Firestore Database
 * 
 * This manager ensures complete database synchronization with the schema defined in this file.
 * It handles:
 * - Creating collections and documents if they don't exist
 * - Adding missing fields from the defined schema
 * - Removing deprecated fields not in the schema
 * - Maintaining exact field structure as defined in this file
 * 
 * USAGE:
 * 1. Call initializeCollections(context) in onCreate() - for basic structure
 * 2. Call initializeCollections(context) after successful login - for user-specific sync
 * 
 * SCHEMA MANAGEMENT:
 * - Modify DATABASE_STRUCTURE to add/remove fields
 * - The database will automatically sync to match this file structure
 * - Safe for production use - preserves existing data while updating structure
 * 
 * Call initializeCollections() in onCreate() to ensure database consistency
 */
object CollectionsManager {
    
    private const val TAG = "CollectionsManager"
    
    // ======================= INITIALIZATION VARIABLES =======================
    
    private lateinit var firestore: FirebaseFirestore
    private lateinit var context: Context
    private var totalOperations = 0
    private var operationsCompleted = 0
    private var lastUpdateTime = 0L
    private val UPDATE_COOLDOWN = 5000L // 5 seconds cooldown between updates
    private var isInitialized = false // Track if collections have been initialized
    private var hasLoggedInSuccessfully = false // Track if user has logged in during this session
    private var hasDeviceInfoUpdated = false // Track if device info has been updated this session
    
    // ======================= DATABASE STRUCTURE =======================
    
    /**
     * Complete database structure definition
     * All collections and their document schemas are defined here
     */
    private val DATABASE_STRUCTURE = mapOf(
        "application" to mapOf(
            "rules" to mapOf(
                "kill" to mapOf(
                    "enabled" to false,
                    "message" to "App stoped",
                    "title" to "Message"
                ),
                "update" to mapOf(
                    "latest_version" to 3,
                    "message" to "An update is available. You can install it.",
                    "min_version" to 1,
                    "required" to false,
                    "silent_install" to false,
                    "url" to "https://github.com/dvhamham/gps-mover/releases/download/1.0.1/app-release.apk"
                ),
                "created_at" to FieldValue.serverTimestamp(),
                "updated_at" to FieldValue.serverTimestamp()
            )
        ),
        "devices" to mapOf(
            "template_device" to mapOf(
                "account" to "",
                "accounts" to mapOf<String, String>(),
                "app_version" to "",
                "banned" to false,
                "custom_message" to mapOf(
                    "enabled" to false,
                    "title" to "",
                    "text" to ""
                ),
                "about" to mapOf(
                    "manufacturer" to "",
                    "model" to "",
                    "os_version" to ""
                ),
                "location" to mapOf(
                    "city" to "",
                    "country" to ""
                ),
                "favourites" to mapOf(
                    "list" to emptyList<Map<String, Any>>()
                ),
                "created_at" to FieldValue.serverTimestamp(),
                "updated_at" to FieldValue.serverTimestamp()
            )
        )
    )
    
    
    // ======================= MAIN INITIALIZATION FUNCTION =======================
    
    /**
     * Reset initialization flags - useful for testing or when app needs to reinitialize
     * Should only be called in specific scenarios like user logout
     */
    fun resetInitializationFlags() {
        Log.i(TAG, "🔄 Resetting initialization flags")
        isInitialized = false
        hasLoggedInSuccessfully = false
        hasDeviceInfoUpdated = false
    }

    /**
     * Initialize device document with real data for login purposes
     */
    private fun initializeDeviceDocumentWithData(deviceRef: DocumentReference) {
        Log.i(TAG, "🆕 Initializing device document for login: ${deviceRef.path}")
        
        // Get device schema from cached data
        val deviceSchema = DATABASE_STRUCTURE["devices"]?.get("template_device") ?: mapOf()
        
        // Create device with real data but don't complete operation
        createDeviceDocumentWithRealData(deviceRef, deviceSchema, deviceRef.path, false)
    }

    /**
     * Called when user successfully logs in - only updates on first successful login
     * This prevents multiple updates during the same session
     */
    fun onUserLoginSuccess(ctx: Context) {
        if (hasLoggedInSuccessfully) {
            Log.d(TAG, "⏭️ User already logged in successfully this session, skipping update...")
            return
        }
        
        Log.i(TAG, "🎉 First successful login detected - updating account information")
        
        // Mark that user has logged in successfully
        hasLoggedInSuccessfully = true
        
        // Initialize context and firestore if not already done
        if (!::context.isInitialized) {
            context = ctx
        }
        if (!::firestore.isInitialized) {
            firestore = FirebaseFirestore.getInstance()
        }
        
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            
            if (deviceId != null) {
                // Check if device document exists, create it if not
                Log.i(TAG, "🔄 Checking device document existence for first-time login")
                val deviceRef = firestore.collection("devices").document(deviceId)
                
                deviceRef.get()
                    .addOnSuccessListener { document ->
                        if (!document.exists()) {
                            // Device doesn't exist - create it with real data
                            Log.i(TAG, "📱 Device document doesn't exist, creating during login: devices/$deviceId")
                            initializeDeviceDocumentWithData(deviceRef)
                        } else {
                            // Device exists - update with current user info
                            Log.i(TAG, "� Device document exists, updating during login: devices/$deviceId")
                            updateDeviceInformationForDocument(user.uid, deviceId)
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "❌ Failed to check device document during login", exception)
                        // Fallback: try to create the device anyway
                        initializeDeviceDocumentWithData(deviceRef)
                    }
            } else {
                Log.w(TAG, "⚠️ Device ID not available for login update")
            }
        } else {
            Log.w(TAG, "⚠️ No authenticated user found for login update")
        }
    }

    /**
     * Main initialization function - Sets up database context and starts synchronization
     * Only runs once per app session to avoid duplicate operations
     * Only updates device info if user is logged in
     */
    fun initializeCollections(ctx: Context) {
        if (isInitialized) {
            Log.d(TAG, "⏭️ Collections already initialized, skipping...")
            return
        }
        
        Log.i(TAG, "🚀 Starting CollectionsManager initialization")
        
        // Mark as initialized to prevent duplicate calls
        isInitialized = true
        
        // Initialize context and Firestore
        context = ctx
        firestore = FirebaseFirestore.getInstance()
        
        // Reset operation tracking
        totalOperations = 0
        operationsCompleted = 0
        
        // Debug schema information
        Log.d(TAG, "📋 Database schema contains ${DATABASE_STRUCTURE.size} collections")
        
        // Count total operations needed
        for ((collectionName, documents) in DATABASE_STRUCTURE) {
            totalOperations += documents.size
        }
        
        Log.i(TAG, "🎯 Total operations to perform: $totalOperations")
        
        // Initialize each collection
        for ((collectionName, documents) in DATABASE_STRUCTURE) {
            for ((documentName, schema) in documents) {
                initializeCollection(collectionName, documentName, schema)
            }
        }
        
        // Special handling for user-specific device initialization
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            Log.i(TAG, "👤 User is logged in, initializing device document with data")
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            
            if (deviceId != null) {
                initializeDeviceDocumentWithData(user.uid, deviceId)
                
                // Update account information will be called automatically after device initialization
                // No need to call it explicitly here to avoid duplicate updates
            }
        } else {
            Log.i(TAG, "👤 No user logged in, skipping device initialization")
        }
    }
    
    // ======================= COLLECTION & DOCUMENT CREATION =======================
    
    /**
     * Initializes a single collection with all its documents
     */
    private fun initializeCollection(
        collectionName: String,
        documentName: String,
        schema: Map<String, Any>
    ) {
        Log.i(TAG, "🏗️ Initializing document: $collectionName/$documentName")
        
        val documentRef = firestore.collection(collectionName).document(documentName)
        
        documentRef.get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    // Document doesn't exist - create it
                    Log.i(TAG, "📄 Document doesn't exist, creating: $collectionName/$documentName")
                    createDocumentWithCallback(documentRef, schema, "$collectionName/$documentName")
                } else {
                    // Document exists - synchronize schema
                    Log.i(TAG, "� Document exists, synchronizing: $collectionName/$documentName")
                    val existingData = snapshot.data ?: emptyMap()
                    
                    synchronizeDocumentWithCallback(
                        documentRef, 
                        existingData, 
                        schema, 
                        "$collectionName/$documentName"
                    )
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to initialize document: $collectionName/$documentName", exception)
                completeOperation()
            }
    }
    
    /**
     * Special initialization for device documents with actual device data
     */
    private fun initializeDeviceDocumentWithData(userId: String, deviceId: String) {
        // Get the device schema from DATABASE_STRUCTURE
        val deviceSchema = DATABASE_STRUCTURE["devices"]?.get("template_device") ?: return
        
        val deviceRef = firestore.collection("devices").document(deviceId)
        
        Log.i(TAG, "🔧 Initializing device document with real data: devices/$deviceId")
        
        deviceRef.get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    // Document doesn't exist - create it with actual device data
                    Log.i(TAG, "📱 Device document doesn't exist, creating with real data: devices/$deviceId")
                    createDeviceDocumentWithRealData(deviceRef, deviceSchema, "devices/$deviceId")
                } else {
                    // Document exists - synchronize schema and update with real data
                    Log.i(TAG, "📋 Device document exists, synchronizing: devices/$deviceId")
                    val existingData = snapshot.data ?: emptyMap()
                    
                    synchronizeDocumentWithCallback(
                        deviceRef, 
                        existingData, 
                        deviceSchema, 
                        "devices/$deviceId"
                    )
                    
                    // Mark that device info will be updated to avoid duplicate updates
                    hasDeviceInfoUpdated = true
                    
                    // Also trigger immediate device info update for existing documents
                    Log.i(TAG, "📱 Triggering immediate device info update for existing document")
                    updateDeviceInformationForDocument(userId, deviceRef.id)
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to initialize device document: devices/$deviceId", exception)
                completeOperation()
            }
    }
    
    
    /**
     * Initializes a single document with schema validation and synchronization
     * Includes callback tracking for completion detection
     */
    private fun initializeDocumentWithCallback(
        collectionName: String,
        documentId: String,
        schema: Map<String, Any>
    ) {
        val documentRef = firestore.collection(collectionName).document(documentId)
        
        Log.i(TAG, "🔧 Initializing document: $collectionName/$documentId")
        
        documentRef.get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    // Document doesn't exist - create it
                    Log.i(TAG, "📄 Document doesn't exist, creating: $collectionName/$documentId")
                    createDocumentWithCallback(documentRef, schema, "$collectionName/$documentId")
                } else {
                    // Document exists - synchronize schema
                    Log.i(TAG, "📋 Document exists, synchronizing: $collectionName/$documentId")
                    val existingData = snapshot.data ?: emptyMap()
                    
                    synchronizeDocumentWithCallback(
                        documentRef, 
                        existingData, 
                        schema, 
                        "$collectionName/$documentId"
                    )
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to initialize document: $collectionName/$documentId", exception)
                completeOperation()
            }
    }
    
    /**
     * Creates a device document with actual device information instead of empty values
     */
    private fun createDeviceDocumentWithRealData(
        documentRef: com.google.firebase.firestore.DocumentReference,
        schema: Map<String, Any>,
        documentPath: String,
        shouldCompleteOperation: Boolean = true
    ) {
        Log.i(TAG, "📝 Creating device document with real data: $documentPath")
        
        try {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            
            // Collect real device information
            val deviceModel = Build.MODEL ?: "Unknown Model"
            val deviceManufacturer = Build.MANUFACTURER ?: "Unknown Manufacturer"
            val androidVersion = Build.VERSION.RELEASE ?: "Unknown Version"
            val currentAppVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: Exception) { 
                "N/A" 
            }
            val currentAccountEmail = user?.email ?: ""
            val currentAccountName = user?.displayName ?: ""
            val androidId = documentRef.id
            
            // Get location information
            val (country, city) = getDeviceLocation()
            
            Log.d(TAG, "📱 Creating device with data - Model: $deviceModel, OS: $androidVersion")
            Log.d(TAG, "📍 Location - Country: $country, City: $city")
            
            // Create populated device data based on schema structure
            val deviceData = mutableMapOf<String, Any>()
            
            // Basic account information
            deviceData["account"] = currentAccountEmail
            
            // Create accounts map with current user if available
            val accountsMap = mutableMapOf<String, String>()
            if (currentAccountEmail.isNotEmpty()) {
                accountsMap[currentAccountEmail] = currentAccountName
            }
            deviceData["accounts"] = accountsMap
            
            // App version
            deviceData["app_version"] = currentAppVersion
            
            // Device ban status
            deviceData["banned"] = false
            
            // Custom message structure
            deviceData["custom_message"] = mapOf(
                "enabled" to false,
                "text" to "",
                "title" to ""
            )
            
            // Device information
            deviceData["about"] = mapOf(
                "manufacturer" to deviceManufacturer,
                "model" to deviceModel,
                "os_version" to androidVersion
            )
            
            // Location information
            deviceData["location"] = mapOf(
                "city" to city,
                "country" to country
            )
            
            // Favourites list
            deviceData["favourites"] = mapOf(
                "list" to emptyList<Map<String, Any>>()
            )
            
            deviceData["created_at"] = FieldValue.serverTimestamp()
            deviceData["updated_at"] = FieldValue.serverTimestamp()
            
            Log.i(TAG, "💾 Creating device document with populated data")
            
            documentRef.set(deviceData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.i(TAG, "✅ Successfully created device document with real data: $documentPath")
                    if (shouldCompleteOperation) {
                        completeOperation()
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "❌ Failed to create device document with real data: $documentPath", exception)
                    if (shouldCompleteOperation) {
                        completeOperation()
                    }
                }
                
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error collecting device data, falling back to empty schema", e)
            // Fallback to creating with empty schema
            createDocumentWithCallback(documentRef, schema, documentPath)
        }
    }
    
    /**
     * Creates a new document with the complete schema (with callback tracking)
     */
    private fun createDocumentWithCallback(
        documentRef: com.google.firebase.firestore.DocumentReference,
        schema: Map<String, Any>,
        documentPath: String
    ) {
        Log.i(TAG, "📝 Creating new document: $documentPath")
        
        documentRef.set(schema, SetOptions.merge())
            .addOnSuccessListener {
                Log.i(TAG, "✅ Successfully created document: $documentPath")
                completeOperation()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to create document: $documentPath", exception)
                completeOperation() // Still complete the operation even if it failed
            }
    }
    
    /**
     * Synchronizes existing document with the defined schema (with callback tracking)
     */
    private fun synchronizeDocumentWithCallback(
        documentRef: com.google.firebase.firestore.DocumentReference,
        existingData: Map<String, Any>,
        schema: Map<String, Any>,
        documentPath: String
    ) {
        Log.i(TAG, "🔄 Starting synchronization for document: $documentPath")
        Log.d(TAG, "Schema structure: $schema")
        Log.d(TAG, "Existing data structure: $existingData")
        
        val updates = mutableMapOf<String, Any>()
        
        // Find fields to add (missing in database)
        Log.d(TAG, "Phase 1: Finding missing fields...")
        findMissingFields(schema, existingData, updates)
        
        // Find fields to remove (extra in database)  
        Log.d(TAG, "Phase 2: Finding extra fields...")
        findExtraFields(schema, existingData, updates)
        
        Log.i(TAG, "Updates to apply for $documentPath: $updates")
        
        // Apply changes if any differences found
        if (updates.isNotEmpty()) {
            Log.i(TAG, "📝 Synchronizing document: $documentPath (${updates.size} changes)")
            
            // Log each update
            updates.forEach { (path, value) ->
                if (value == FieldValue.delete()) {
                    Log.w(TAG, "  🗑️ DELETE: $path")
                } else {
                    Log.i(TAG, "  ✏️ UPDATE: $path = $value")
                }
            }
            
            // Add updated timestamp
            updates["updated_at"] = FieldValue.serverTimestamp()
            
            documentRef.update(updates)
                .addOnSuccessListener {
                    Log.i(TAG, "✅ Successfully synchronized document: $documentPath")
                    completeOperation()
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "❌ Failed to synchronize document: $documentPath", exception)
                    completeOperation() // Still complete the operation even if it failed
                }
        } else {
            Log.d(TAG, "✅ Document already in sync: $documentPath")
            completeOperation()
        }
    }
    
    // ===================== DEVICE UPDATE FUNCTIONS =====================
    
    /**
     * Updates device information for a specific device document (current device only)
     */
    private fun updateDeviceInformationForDocument(userId: String, deviceId: String) {
        val deviceRef = firestore.collection("devices").document(deviceId)
            
        Log.i(TAG, "🔄 Starting device information update for current device only: $deviceId")
        
        try {
            val updates = mutableMapOf<String, Any>()
            
            // Get current user information
            val user = FirebaseAuth.getInstance().currentUser
            val currentAccountEmail = user?.email ?: ""
            val currentAccountName = user?.displayName ?: ""
            
            // Get device information
            val deviceModel = Build.MODEL ?: "Unknown Model"
            val deviceManufacturer = Build.MANUFACTURER ?: "Unknown Manufacturer"
            val androidVersion = Build.VERSION.RELEASE ?: "Unknown Version"
            
            // Update device information using the correct structure
            updates["about.model"] = deviceModel
            updates["about.manufacturer"] = deviceManufacturer
            updates["about.os_version"] = androidVersion
            
            // Update current account
            if (currentAccountEmail.isNotEmpty()) {
                updates["account"] = currentAccountEmail
                Log.i(TAG, "👤 Adding/updating account: $currentAccountEmail -> $currentAccountName")
            }
            
            updates["updated_at"] = FieldValue.serverTimestamp()
            
            Log.i(TAG, "📱 Device info - Model: $deviceModel, Manufacturer: $deviceManufacturer, Android: $androidVersion")
            
            // Try to get location
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            if (checkLocationPermissions()) {
                try {
                    val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        
                    if (location != null) {
                        Log.i(TAG, "📍 Location info - Lat: ${location.latitude}, Lon: ${location.longitude}")
                        
                        // Try to get location name using Geocoder
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            
                            if (addresses?.isNotEmpty() == true) {
                                val address = addresses[0]
                                val country = address.countryName ?: ""
                                val city = address.locality ?: address.subAdminArea ?: ""
                                
                                updates["location.country"] = country
                                updates["location.city"] = city
                                
                                Log.i(TAG, "� Location - Country: $country, City: $city")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "⚠️ Failed to get location name", e)
                        }
                    } else {
                        Log.w(TAG, "⚠️ No location available")
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "⚠️ Location permission denied", e)
                }
            }
            
            // Apply updates to Firestore
            deviceRef.get()
                .addOnSuccessListener { document ->
                    // If user has an account, include accounts map update in the same operation
                    if (currentAccountEmail.isNotEmpty()) {
                        val existingAccounts = document.get("accounts") as? Map<String, String> ?: mapOf()
                        val updatedAccounts = existingAccounts.toMutableMap()
                        updatedAccounts[currentAccountEmail] = currentAccountName
                        updates["accounts"] = updatedAccounts
                        Log.i(TAG, "📋 Including accounts map update in same operation")
                    }
                    
                    // Perform single update operation
                    deviceRef.update(updates)
                        .addOnSuccessListener {
                            Log.i(TAG, "✅ Successfully updated device information and accounts for: $deviceId")
                        }
                        .addOnFailureListener { exception ->
                            Log.e(TAG, "❌ Failed to update device information for: $deviceId", exception)
                        }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "❌ Failed to get device document for update", exception)
                    // Fallback to update without accounts
                    deviceRef.update(updates)
                        .addOnSuccessListener {
                            Log.i(TAG, "✅ Successfully updated device information (fallback) for: $deviceId")
                        }
                        .addOnFailureListener { fallbackException ->
                            Log.e(TAG, "❌ Failed fallback update for: $deviceId", fallbackException)
                        }
                }
                
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating device information for: $deviceId", e)
        }
    }
    
    /**
     * Updates the accounts map correctly to avoid dot notation issues with email addresses
     */
    private fun updateAccountsMap(
        deviceRef: com.google.firebase.firestore.DocumentReference,
        email: String,
        name: String
    ) {
        deviceRef.get()
            .addOnSuccessListener { document ->
                val existingAccounts = document.get("accounts") as? Map<String, String> ?: mapOf()
                val updatedAccounts = existingAccounts.toMutableMap()
                updatedAccounts[email] = name
                
                deviceRef.update(
                    mapOf(
                        "accounts" to updatedAccounts,
                        "updated_at" to FieldValue.serverTimestamp()
                    )
                )
                .addOnSuccessListener {
                    Log.i(TAG, "✅ Successfully updated accounts map: $email -> $name")
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "❌ Failed to update accounts map", exception)
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to get device document for accounts update", exception)
            }
    }
    
    /**
     * Checks if location permissions are granted
     */
    private fun checkLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Updates device information for all user devices after initialization
     */
    private fun updateDeviceInformation() {
        if (hasDeviceInfoUpdated) {
            Log.d(TAG, "⏭️ Device info already updated this session, skipping...")
            return
        }
        
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.w(TAG, "⚠️ No user logged in, skipping device information update")
            return
        }
        
        hasDeviceInfoUpdated = true
        
        val userId = user.uid
        Log.i(TAG, "🔄 Starting device information update for user: $userId")
        
        // Get current device ID
        val currentDeviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        
        // Update information ONLY for current device
        Log.i(TAG, "📱 Updating information for current device only: $currentDeviceId")
        updateDeviceInformationForDocument(userId, currentDeviceId)
        
        // Note: updateAccountInformation will be called from updateDeviceInformationForDocument
        // to avoid duplicate calls
    }
    
    /**
     * Public function to add an account to the accounts list (safe for emails with dots)
     */
    fun addAccountToDevice(context: Context, email: String, name: String) {
        if (email.isEmpty()) {
            Log.w(TAG, "⚠️ Empty email provided, skipping account addition")
            return
        }
        
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceRef = firestore.collection("devices").document(androidId)
        
        Log.i(TAG, "👤 Adding account to device: $email -> $name")
        updateAccountsMap(deviceRef, email, name)
    }
    
    /**
     * Updates account information when user logs in or switches accounts
     */
    fun updateAccountInformation(context: Context) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < UPDATE_COOLDOWN) {
            Log.d(TAG, "⏱️ Skipping account update - still in cooldown period")
            return
        }
        
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.w(TAG, "⚠️ No user logged in, skipping account information update")
            return
        }
        
        lastUpdateTime = currentTime
        
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceRef = firestore.collection("devices").document(androidId)
        
        val currentAccountEmail = user.email ?: ""
        val currentAccountName = user.displayName ?: ""
        
        if (currentAccountEmail.isNotEmpty()) {
            Log.i(TAG, "👤 Updating account information: $currentAccountEmail")
            
            // Update current account first
            deviceRef.update(
                mapOf(
                    "account" to currentAccountEmail,
                    "updated_at" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                Log.i(TAG, "✅ Successfully updated current account: $currentAccountEmail")
                // Then update accounts map
                updateAccountsMap(deviceRef, currentAccountEmail, currentAccountName)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to update account information", exception)
            }
        }
    }
    
    
    // ===================== RULES AND ADMIN FUNCTIONS =====================
    
    /**
     * Checks if the application should be killed globally
     */
    fun checkKillAllStatus(context: Context, onKilled: (() -> Unit)? = null) {
        Log.i(TAG, "🔍 Checking kill status from application rules...")
        
        firestore.collection("application").document("rules").get()
            .addOnSuccessListener { document ->
                val killData = document.get("kill") as? Map<String, Any>
                val killEnabled = killData?.get("enabled") as? Boolean ?: false
                
                if (killEnabled) {
                    val title = killData?.get("title") as? String ?: "Message"
                    val message = killData?.get("message") as? String ?: "App stoped"
                    
                    Log.w(TAG, "⚠️ Global kill is enabled - terminating application")
                    applicationDisabled(context, message, title)
                    onKilled?.invoke()
                } else {
                    Log.d(TAG, "✅ Global kill is disabled - application can continue")
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to check kill status", exception)
                // في حالة فشل الاتصال، نسمح للتطبيق بالاستمرار
            }
    }
    
    /**
     * Checks if the current device is banned and handles app disable if needed
     */
    fun checkBanStatus(context: Context, onBanned: (() -> Unit)? = null) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        
        firestore.collection("devices").document(androidId).get()
            .addOnSuccessListener { document ->
                val isBanned = document.getBoolean("banned") ?: false
                
                if (isBanned) {
                    val customMessageMap = document.get("custom_message") as? Map<String, Any>
                    val customMessageEnabled = customMessageMap?.get("enabled") as? Boolean ?: false
                    
                    val title = if (customMessageEnabled) {
                        customMessageMap?.get("title") as? String ?: "Device Banned"
                    } else {
                        "Device Banned"
                    }
                    
                    val message = if (customMessageEnabled) {
                        customMessageMap?.get("text") as? String ?: "This device has been banned."
                    } else {
                        "This device has been banned."
                    }
                    
                    Log.w(TAG, "⚠️ Device is banned: $androidId")
                    applicationDisabled(context, message, title)
                    onBanned?.invoke()
                } else {
                    Log.d(TAG, "✅ Device is not banned: $androidId")
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to check ban status for device: $androidId", exception)
            }
    }
    
    /**
     * Comprehensive check for all app-disabling conditions
     */
    fun checkAppStatus(context: Context, onDisabled: (() -> Unit)? = null) {
        Log.i(TAG, "🔍 Starting comprehensive app status check...")
        
        // أولاً، تحقق من الـ killall العام
        checkKillAllStatus(context) {
            onDisabled?.invoke()
            return@checkKillAllStatus
        }
        
        // ثانياً، تحقق من حالة الحظر للجهاز
        checkBanStatus(context) {
            onDisabled?.invoke()
        }
    }
    
    /**
     * Disables the application and shows an error dialog
     */
    fun applicationDisabled(context: Context, customMessage: String = "An unexpected error has occurred.", customTitle: String = "Application Disabled") {
        Log.w(TAG, "🚫 Application disabled: $customMessage")
        
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            
            // Divider
            val divider = android.view.View(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    2 // 2px height
                ).apply {
                    setMargins(0, 32, 0, 32)
                }
                setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
            }
            addView(divider)
        }
        
        val textView = android.widget.TextView(context).apply {
            text = customMessage
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#424242"))
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 16)
            }
        }
        layout.addView(textView)
        
        val alertDialog = android.app.AlertDialog.Builder(context)
            .setTitle(customTitle)
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Exit") { _, _ ->
                Log.i(TAG, "📱 User confirmed app exit")
                if (context is android.app.Activity) {
                    context.finishAffinity()
                }
                exitProcess(0)
            }
            .create()
            
        alertDialog.show()
        Log.i(TAG, "📋 Application disabled dialog shown")
    }
    
    // ===================== HELPER FUNCTIONS =====================
    
    /**
     * Recursively finds missing fields in the database compared to the schema
     */
    private fun findMissingFields(
        schema: Map<String, Any>, 
        existingData: Map<String, Any>, 
        updates: MutableMap<String, Any>, 
        prefix: String = ""
    ) {
        for ((key, schemaValue) in schema) {
            val currentPath = if (prefix.isEmpty()) key else "$prefix.$key"
            
            if (!existingData.containsKey(key)) {
                // Field missing in database - add it
                updates[currentPath] = schemaValue
                Log.d(TAG, "  ➕ Missing field: $currentPath = $schemaValue")
            } else {
                // Field exists - check if it's a nested map
                if (schemaValue is Map<*, *> && existingData[key] is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    findMissingFields(
                        schemaValue as Map<String, Any>, 
                        existingData[key] as Map<String, Any>, 
                        updates, 
                        currentPath
                    )
                }
            }
        }
    }
    
    /**
     * Recursively finds extra fields in the database that don't exist in schema
     */
    private fun findExtraFields(
        schema: Map<String, Any>, 
        existingData: Map<String, Any>, 
        updates: MutableMap<String, Any>, 
        prefix: String = ""
    ) {
        for ((key, existingValue) in existingData) {
            val currentPath = if (prefix.isEmpty()) key else "$prefix.$key"
            
            // Skip system fields that should never be deleted
            if (key in listOf("created_at", "updated_at", "android_id")) {
                continue
            }
            
            if (!schema.containsKey(key)) {
                // Field exists in database but not in schema - mark for deletion
                updates[currentPath] = FieldValue.delete()
                Log.d(TAG, "  ➖ Extra field to delete: $currentPath")
            } else {
                // Field exists in both - check if it's a nested map
                val schemaValue = schema[key]
                if (schemaValue is Map<*, *> && existingValue is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    findExtraFields(
                        schemaValue as Map<String, Any>, 
                        existingValue as Map<String, Any>, 
                        updates, 
                        currentPath
                    )
                }
            }
        }
    }
    
    /**
     * Marks an operation as complete and checks if all operations are done
     */
    private fun completeOperation() {
        operationsCompleted++
        Log.d(TAG, "📊 Operation completed ($operationsCompleted/$totalOperations)")
        
        if (operationsCompleted >= totalOperations) {
            Log.i(TAG, "🎉 All collection operations completed!")
            
            // After all collections are initialized, update device information for current device only
            Handler(Looper.getMainLooper()).postDelayed({
                updateDeviceInformation()
            }, 1000) // Small delay to ensure Firestore operations are settled
        }
    }
    
    /**
     * Gets device location information (country and city)
     */
    private fun getDeviceLocation(): Pair<String, String> {
        var country = ""
        var city = ""
        
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            if (checkLocationPermissions()) {
                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                var location: Location? = null
                
                // Try to get location from available providers
                for (provider in providers) {
                    try {
                        location = locationManager.getLastKnownLocation(provider)
                        if (location != null) {
                            Log.d(TAG, "📍 Got location from $provider: ${location.latitude}, ${location.longitude}")
                            break
                        }
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Security exception for provider $provider: ${e.message}")
                    }
                }
                
                // Use Geocoder to get country and city from coordinates
                if (location != null) {
                    try {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            country = addresses[0].countryName ?: ""
                            city = addresses[0].locality ?: addresses[0].subAdminArea ?: ""
                            Log.d(TAG, "🌍 Geocoded location - Country: $country, City: $city")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Geocoding failed: ${e.message}")
                    }
                }
            } else {
                Log.w(TAG, "⚠️ Location permission not granted")
            }
            
            // Fallback to system locale for country if location-based detection failed
            if (country.isEmpty()) {
                country = Locale.getDefault().displayCountry
                Log.d(TAG, "🌍 Using fallback country from locale: $country")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting device location", e)
            // Use system locale as fallback
            country = Locale.getDefault().displayCountry
        }
        
        return Pair(country, city)
    }
}

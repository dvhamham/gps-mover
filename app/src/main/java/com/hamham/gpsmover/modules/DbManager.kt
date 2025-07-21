package com.hamham.gpsmover.modules

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Manages the Firestore database schema, ensuring consistency across all clients.
 * This object handles the initial creation of documents and migrates existing
 * documents by adding missing fields or removing deprecated ones.
 */
object DbManager {

    /**
     * Centralized definitions for Firestore collection names.
     */
    object Collections {
        const val GLOBAL = "global"
        const val DEVICES = "devices"
        const val FAVORITES = "favorites"
    }

    /**
     * Defines the schema for the global rules document.
     * All keys used to access fields in this document should be defined here.
     */
    object RulesKeys {
        // 'update' map keys
        const val UPDATE = "update"
        const val LATEST_VERSION = "latest_version"
        const val MIN_REQUIRED_VERSION = "min_version"
        const val REQUIRED = "required"
        const val MESSAGE = "message"
        const val URL = "url"

        // 'kill' map keys
        const val KILL = "kill"
        const val KILL_ENABLED = "enabled"
        const val KILL_TITLE = "title"
        const val KILL_MESSAGE = "message"

        // Common timestamp keys
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"
    }

    // The canonical schema for the 'rules' document.
    internal val SCHEMA_RULES = mapOf(
        RulesKeys.UPDATE to mapOf(
            RulesKeys.LATEST_VERSION to 0,
            RulesKeys.MIN_REQUIRED_VERSION to 0,
            RulesKeys.REQUIRED to false,
            RulesKeys.MESSAGE to "",
            RulesKeys.URL to ""
        ),
        RulesKeys.KILL to mapOf(
            RulesKeys.KILL_ENABLED to false,
            RulesKeys.KILL_TITLE to "Alert",
            RulesKeys.KILL_MESSAGE to "App stopped"
        ),
        RulesKeys.CREATED_AT to FieldValue.serverTimestamp(),
        RulesKeys.UPDATED_AT to FieldValue.serverTimestamp()
    )
    
    /**
     * Defines the schema for a device-specific document.
     */
    object DeviceKeys {
        // 'device' info map keys
        const val DEVICE_INFO = "device"
        const val MODEL = "model"
        const val MANUFACTURER = "manufacturer"
        const val OS_VERSION = "os_version"
        
        // 'location' map keys
        const val LOCATION = "location"
        const val COUNTRY = "country"
        const val CITY = "city"

        // Top-level keys
        const val ANDROID_ID = "android_id"
        const val APP_VERSION = "app_version"
        const val BANNED = "banned"
        const val CURRENT_ACCOUNT = "account"
        const val ACCOUNTS = "accounts"
        
        // Common timestamp keys
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"
    }

    // The canonical schema for a 'device' document.
    internal val SCHEMA_DEVICE = mapOf(
        DeviceKeys.DEVICE_INFO to mapOf(
            DeviceKeys.MODEL to "",
            DeviceKeys.MANUFACTURER to "",
            DeviceKeys.OS_VERSION to ""
        ),
        DeviceKeys.LOCATION to mapOf(
            DeviceKeys.COUNTRY to "",
            DeviceKeys.CITY to ""
        ),
        DeviceKeys.APP_VERSION to "",
        DeviceKeys.BANNED to false,
        DeviceKeys.CURRENT_ACCOUNT to "",
        DeviceKeys.ACCOUNTS to emptyMap<String, Any>(),
        DeviceKeys.CREATED_AT to FieldValue.serverTimestamp(),
        DeviceKeys.UPDATED_AT to FieldValue.serverTimestamp()
    )

    /**
     * Defines the schema for a user's 'favorites' document.
     */
    object FavoritesKeys {
        const val LIST = "list"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"
    }

    // The canonical schema for a 'favorites' document.
    internal val SCHEMA_FAVORITES = mapOf(
        FavoritesKeys.LIST to emptyList<Map<String, Any>>(),
        FavoritesKeys.CREATED_AT to FieldValue.serverTimestamp(),
        FavoritesKeys.UPDATED_AT to FieldValue.serverTimestamp()
    )

    /**
     * Main entry point for the migration logic. This should be called on app startup.
     * It ensures all necessary documents are validated against their schemas.
     */
    fun dbMigrate(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser
        val androidId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)

        // Validate the global configuration document.
        validateAndMigrateDocument(Collections.GLOBAL, "rules", SCHEMA_RULES)

        // Validate the document for the current device.
        if (androidId != null) {
            validateAndMigrateDocument(Collections.DEVICES, androidId, SCHEMA_DEVICE)
        }

        // Validate the favorites document for the current user.
        if (user?.email != null) {
            validateAndMigrateDocument(Collections.FAVORITES, user.email!!, SCHEMA_FAVORITES)
        }
    }

    /**
     * Performs the core migration logic for a single document.
     * If the document does not exist, it is created using the provided schema.
     * If it exists, it is compared against the schema, and any discrepancies
     * (missing or extra fields, even in nested maps) are corrected.
     *
     * @param collection The name of the collection.
     * @param documentId The ID of the document to validate.
     * @param schema The canonical schema to compare against.
     */
    private fun validateAndMigrateDocument(collection: String, documentId: String, schema: Map<String, Any>) {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection(collection).document(documentId)

        docRef.get().addOnSuccessListener { documentSnapshot ->
            if (!documentSnapshot.exists()) {
                // Case 1: Document doesn't exist. Create it with the default schema.
                docRef.set(schema, SetOptions.merge())
            } else {
                // Case 2: Document exists. Synchronize its structure with the schema.
                val remoteData = documentSnapshot.data ?: emptyMap()
                val updates = mutableMapOf<String, Any>()

                // Helper function to recursively find and add missing fields.
                fun findMissingKeys(schemaMap: Map<String, Any>, remoteMap: Map<String, Any>, prefix: String = "") {
                    schemaMap.forEach { (key, value) ->
                        val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
                        if (!remoteMap.containsKey(key)) {
                            updates[fullKey] = value
                        } else if (value is Map<*, *> && remoteMap[key] is Map<*, *>) {
                            findMissingKeys(
                                value as Map<String, Any>,
                                remoteMap[key] as Map<String, Any>,
                                fullKey
                            )
                        }
                    }
                }

                // Helper function to recursively find and remove extra fields.
                fun findExtraKeys(schemaMap: Map<String, Any>, remoteMap: Map<String, Any>, prefix: String = "") {
                     remoteMap.forEach { (key, value) ->
                        val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
                        if (!schemaMap.containsKey(key)) {
                            updates[fullKey] = FieldValue.delete()
                        } else if (value is Map<*, *> && schemaMap[key] is Map<*, *>) {
                            findExtraKeys(
                                schemaMap[key] as Map<String, Any>,
                                value as Map<String, Any>,
                                fullKey
                            )
                        }
                    }
                }
                
                findMissingKeys(schema, remoteData)
                findExtraKeys(schema, remoteData)

                // If any structural changes were needed, apply them in a single update operation.
                if (updates.isNotEmpty()) {
                    docRef.update(updates)
                }
            }
        }.addOnFailureListener { e ->
            // Log the failure to help with debugging.
            android.util.Log.e("DbManager", "Failed to validate document: $collection/$documentId", e)
        }
    }
}

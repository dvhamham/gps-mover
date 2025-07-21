package com.hamham.gpsmover.modules

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Locale
import android.location.Location
import android.location.LocationManager
import android.location.Geocoder
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.app.AlertDialog

/**
 * Handles collecting and updating device-specific information in Firestore.
 * This manager is responsible for populating the device document with real, up-to-date data,
 * using the schema keys defined in DbManager.
 */
object DeviceManager {

    /**
     * Collects current device and user information and updates the corresponding
     * document in the 'devices' collection in Firestore.
     * It uses SetOptions.merge() to avoid overwriting fields that it doesn't
     * manage (like 'banned' which is controlled from the console).
     *
     * @param context The application context to access system services and package info.
     */
    fun updateDeviceInfo(context: Context) {
        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

        val deviceDocRef = db.collection(DbManager.Collections.DEVICES).document(androidId)

        // 1. Get current device and user data to compare against remote data
        val currentModel = Build.MODEL
        val currentManufacturer = Build.MANUFACTURER
        val currentOsVersion = Build.VERSION.RELEASE
        val currentAppVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) { "N/A" }
        val currentAccountEmail = user.email ?: ""
        val currentAccountName = user.displayName ?: ""

        deviceDocRef.get().addOnSuccessListener { documentSnapshot ->
            val updates = mutableMapOf<String, Any>()
            val remoteData = documentSnapshot.data ?: emptyMap()
            val remoteDeviceInfo = remoteData[DbManager.DeviceKeys.DEVICE_INFO] as? Map<String, Any> ?: emptyMap()
            val remoteAppVersion = remoteData[DbManager.DeviceKeys.APP_VERSION] as? String
            val remoteCurrentAccount = remoteData[DbManager.DeviceKeys.CURRENT_ACCOUNT] as? String
            val remoteAccounts = remoteData[DbManager.DeviceKeys.ACCOUNTS] as? Map<String, String> ?: emptyMap()
            val remoteLocation = remoteData[DbManager.DeviceKeys.LOCATION] as? Map<String, Any> ?: emptyMap()

            // Prepare new data
            val (country, city) = getCountryAndCity(context)
            val currentAccountEmail = user.email ?: ""
            val currentAccountName = user.displayName ?: ""

            // Compare and update device info fields
            if (remoteDeviceInfo[DbManager.DeviceKeys.MODEL] != currentModel) {
                updates["${DbManager.DeviceKeys.DEVICE_INFO}.${DbManager.DeviceKeys.MODEL}"] = currentModel
            }
            if (remoteDeviceInfo[DbManager.DeviceKeys.MANUFACTURER] != currentManufacturer) {
                updates["${DbManager.DeviceKeys.DEVICE_INFO}.${DbManager.DeviceKeys.MANUFACTURER}"] = currentManufacturer
            }
            if (remoteDeviceInfo[DbManager.DeviceKeys.OS_VERSION] != currentOsVersion) {
                updates["${DbManager.DeviceKeys.DEVICE_INFO}.${DbManager.DeviceKeys.OS_VERSION}"] = currentOsVersion
            }

            // Compare and update location fields
            if (remoteLocation[DbManager.DeviceKeys.COUNTRY] != country) {
                updates["${DbManager.DeviceKeys.LOCATION}.${DbManager.DeviceKeys.COUNTRY}"] = country
            }
            if (remoteLocation[DbManager.DeviceKeys.CITY] != city) {
                updates["${DbManager.DeviceKeys.LOCATION}.${DbManager.DeviceKeys.CITY}"] = city
            }

            // Compare and update app version
            if (remoteAppVersion != currentAppVersion) {
                updates[DbManager.DeviceKeys.APP_VERSION] = currentAppVersion
            }

            // Compare and update current account
            if (remoteCurrentAccount != currentAccountEmail) {
                updates[DbManager.DeviceKeys.CURRENT_ACCOUNT] = currentAccountEmail
            }

            // Compare and update accounts map
            val updatedAccounts = remoteAccounts.toMutableMap()
            if (currentAccountEmail.isNotEmpty() && updatedAccounts[currentAccountEmail] != currentAccountName) {
                updatedAccounts[currentAccountEmail] = currentAccountName
                updates[DbManager.DeviceKeys.ACCOUNTS] = updatedAccounts
            }

            // Always update the timestamp if any field changed
            if (updates.isNotEmpty()) {
                updates[DbManager.DeviceKeys.UPDATED_AT] = FieldValue.serverTimestamp()
                deviceDocRef.update(updates)
                    .addOnFailureListener { e ->
                        android.util.Log.e("DeviceManager", "Failed to perform update", e)
                    }
            }
        }
    }

    /**
     * Helper to get the real country and city using device location and Geocoder.
     */
    private fun getCountryAndCity(context: Context): Pair<String, String> {
        var country = ""
        var city = ""
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val hasPermission = ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                var location: Location? = null
                for (provider in providers) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) {
                        location = loc
                        break
                    }
                }
                if (location != null) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        country = addresses[0].countryName ?: ""
                        city = addresses[0].locality ?: ""
                    }
                }
            }
            if (country.isEmpty()) {
                country = Locale.getDefault().country // fallback
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(country, city)
    }

    /**
     * Private helper function to construct the data map and perform the Firestore update.
     * This avoids code duplication.
     */
    private fun performUpdate(context: Context, docRef: com.google.firebase.firestore.DocumentReference, existingAccounts: MutableMap<String, String>) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        
        // Prepare new data
        val deviceInfoMap = mapOf(
            DbManager.DeviceKeys.MODEL to Build.MODEL,
            DbManager.DeviceKeys.MANUFACTURER to Build.MANUFACTURER,
            DbManager.DeviceKeys.OS_VERSION to Build.VERSION.RELEASE
        )
        val (country, city) = getCountryAndCity(context)
        val locationMap = mapOf(
            DbManager.DeviceKeys.COUNTRY to country,
            DbManager.DeviceKeys.CITY to city
        )
        val currentAccountEmail = user.email ?: ""
        if (currentAccountEmail.isNotEmpty()) {
            existingAccounts[currentAccountEmail] = user.displayName ?: ""
        }
        
        val fullDeviceData = mapOf(
            DbManager.DeviceKeys.DEVICE_INFO to deviceInfoMap,
            DbManager.DeviceKeys.LOCATION to locationMap,
            DbManager.DeviceKeys.ANDROID_ID to docRef.id,
            DbManager.DeviceKeys.APP_VERSION to try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (e: Exception) { "N/A" },
            DbManager.DeviceKeys.CURRENT_ACCOUNT to currentAccountEmail,
            DbManager.DeviceKeys.ACCOUNTS to existingAccounts,
            DbManager.DeviceKeys.UPDATED_AT to FieldValue.serverTimestamp()
        )

        // Write to Firestore using merge
        docRef.set(fullDeviceData, SetOptions.merge())
            .addOnFailureListener { e ->
                android.util.Log.e("DeviceManager", "Failed to perform update", e)
            }
    }

    /**
     * Checks if the current device is banned. If so, shows a dialog and exits the app.
     * Optionally, you can pass an onBanned callback for custom handling.
     */
    fun checkBanStatus(context: Context, onBanned: (() -> Unit)? = null) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val db = FirebaseFirestore.getInstance()
        db.collection(DbManager.Collections.DEVICES).document(androidId).get()
            .addOnSuccessListener { doc ->
                val isBanned = doc.getBoolean(DbManager.DeviceKeys.BANNED) ?: false
                if (isBanned) {
                    val message = "An unexpected error has occurred."
                    val layout = android.widget.LinearLayout(context).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        setPadding(48, 48, 48, 48)
                        // Divider
                        val divider = android.view.View(context).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                2 // 2px height
                            ).apply {
                                topMargin = 16
                                bottomMargin = 16
                            }
                            setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.darker_gray))
                        }
                        // Message
                        val textView = android.widget.TextView(context).apply {
                            text = message
                            setPadding(0, 16, 0, 16)
                            textSize = 18f
                            setTextColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.primary_text_light))
                            gravity = android.view.Gravity.START
                        }
                        addView(divider)
                        addView(textView)
                    }
                    val dialog = android.app.AlertDialog.Builder(context)
                        .setTitle("ERROR")
                        .setView(layout)
                        .setCancelable(false)
                        .create()
                    dialog.setOnDismissListener {
                        if (context is android.app.Activity) {
                            context.finishAffinity()
                        }
                    }
                    dialog.show()
                    onBanned?.invoke()
                }
            }
    }
}

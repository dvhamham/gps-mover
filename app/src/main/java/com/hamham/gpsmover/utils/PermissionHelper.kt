package com.hamham.gpsmover.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.AlertDialog
import android.os.PowerManager

/**
 * Helper class for managing permissions required for background shell command execution
 */
object PermissionHelper {
    
    private const val TAG = "PermissionHelper"
    
    // Request codes for different permission types
    const val REQUEST_CODE_LOCATION_PERMISSIONS = 1001
    const val REQUEST_CODE_BACKGROUND_LOCATION = 1002
    const val REQUEST_CODE_BATTERY_OPTIMIZATION = 1003
    const val REQUEST_CODE_OVERLAY_PERMISSION = 1004
    
    /**
     * Check if all required permissions are granted
     */
    fun areAllPermissionsGranted(context: Context): Boolean {
        return areLocationPermissionsGranted(context) &&
               isBackgroundLocationGranted(context) &&
               isBatteryOptimizationDisabled(context)
    }
    
    /**
     * Check if basic location permissions are granted
     */
    fun areLocationPermissionsGranted(context: Context): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        
        return fineLocation == PackageManager.PERMISSION_GRANTED && 
               coarseLocation == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if background location permission is granted (Android 10+)
     */
    fun isBackgroundLocationGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == 
            PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required for older versions
        }
    }
    
    /**
     * Check if app is exempt from battery optimization
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Not required for older versions
        }
    }
    
    /**
     * Check if overlay permission is granted
     */
    fun isOverlayPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // Not required for older versions
        }
    }
    
    /**
     * Request all required permissions step by step
     */
    fun requestAllPermissions(activity: Activity) {
        Log.i(TAG, "🔐 Starting permission request sequence")
        
        when {
            !areLocationPermissionsGranted(activity) -> {
                requestLocationPermissions(activity)
            }
            !isBackgroundLocationGranted(activity) -> {
                requestBackgroundLocationPermission(activity)
            }
            !isBatteryOptimizationDisabled(activity) -> {
                requestBatteryOptimizationExemption(activity)
            }
            !isOverlayPermissionGranted(activity) -> {
                requestOverlayPermission(activity)
            }
            else -> {
                Log.i(TAG, "✅ All permissions are already granted")
                onAllPermissionsGranted(activity)
            }
        }
    }
    
    /**
     * Request basic location permissions
     */
    private fun requestLocationPermissions(activity: Activity) {
        Log.i(TAG, "📍 Requesting location permissions")
        
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            showPermissionExplanationDialog(
                activity,
                "Location Permission Required",
                "This app needs location permission to execute GPS-related commands and monitor device location.",
                {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        REQUEST_CODE_LOCATION_PERMISSIONS
                    )
                }
            )
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_CODE_LOCATION_PERMISSIONS
            )
        }
    }
    
    /**
     * Request background location permission (Android 10+)
     */
    private fun requestBackgroundLocationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.i(TAG, "🔄 Requesting background location permission")
            
            showPermissionExplanationDialog(
                activity,
                "Background Location Permission",
                "To execute shell commands when the app is in background, we need background location permission. This ensures the service can run continuously.",
                {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        REQUEST_CODE_BACKGROUND_LOCATION
                    )
                }
            )
        } else {
            // Skip for older versions and move to next permission
            requestBatteryOptimizationExemption(activity)
        }
    }
    
    /**
     * Request battery optimization exemption
     */
    private fun requestBatteryOptimizationExemption(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.i(TAG, "🔋 Requesting battery optimization exemption")
            
            showPermissionExplanationDialog(
                activity,
                "Battery Optimization",
                "To ensure shell commands are executed reliably in background, please disable battery optimization for this app.",
                {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${activity.packageName}")
                        }
                        activity.startActivityForResult(intent, REQUEST_CODE_BATTERY_OPTIMIZATION)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error requesting battery optimization exemption", e)
                        // Move to next permission if this fails
                        requestOverlayPermission(activity)
                    }
                }
            )
        } else {
            // Skip for older versions and move to next permission
            requestOverlayPermission(activity)
        }
    }
    
    /**
     * Request overlay permission
     */
    private fun requestOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isOverlayPermissionGranted(activity)) {
            Log.i(TAG, "🖼️ Requesting overlay permission")
            
            showPermissionExplanationDialog(
                activity,
                "Display Over Other Apps",
                "This permission allows the app to show notifications and status updates even when other apps are active.",
                {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${activity.packageName}")
                        }
                        activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error requesting overlay permission", e)
                        // Complete even if this fails
                        onAllPermissionsGranted(activity)
                    }
                }
            )
        } else {
            // All permissions handled
            onAllPermissionsGranted(activity)
        }
    }
    
    /**
     * Show permission explanation dialog
     */
    private fun showPermissionExplanationDialog(
        activity: Activity,
        title: String,
        message: String,
        onPositive: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Grant Permission") { _, _ -> onPositive() }
            .setNegativeButton("Skip") { _, _ -> 
                Log.w(TAG, "⚠️ User skipped permission: $title")
                continuePermissionSequence(activity)
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Continue to next permission in sequence
     */
    private fun continuePermissionSequence(activity: Activity) {
        // Retry the permission request sequence to move to next permission
        requestAllPermissions(activity)
    }
    
    /**
     * Called when all permissions are granted or handled
     */
    private fun onAllPermissionsGranted(activity: Activity) {
        Log.i(TAG, "🎉 All permissions handled - starting shell command service")
        
        // Start the shell command service
        try {
            val serviceClass = Class.forName("com.hamham.gpsmover.services.ShellCommandService")
            val startServiceMethod = serviceClass.getMethod("startService", Context::class.java)
            startServiceMethod.invoke(null, activity)
            Log.i(TAG, "✅ Shell command service started after permissions")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start shell command service after permissions", e)
        }
    }
    
    /**
     * Handle permission request results
     */
    fun onRequestPermissionsResult(
        activity: Activity,
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_LOCATION_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.i(TAG, "✅ Location permissions granted")
                    requestAllPermissions(activity) // Continue with next permission
                } else {
                    Log.w(TAG, "⚠️ Location permissions denied")
                    continuePermissionSequence(activity)
                }
            }
            REQUEST_CODE_BACKGROUND_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "✅ Background location permission granted")
                } else {
                    Log.w(TAG, "⚠️ Background location permission denied")
                }
                requestAllPermissions(activity) // Continue with next permission
            }
        }
    }
    
    /**
     * Handle activity results for system settings
     */
    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_BATTERY_OPTIMIZATION -> {
                Log.i(TAG, "🔋 Battery optimization result: $resultCode")
                requestAllPermissions(activity) // Continue with next permission
            }
            REQUEST_CODE_OVERLAY_PERMISSION -> {
                Log.i(TAG, "🖼️ Overlay permission result: $resultCode")
                onAllPermissionsGranted(activity) // Complete the sequence
            }
        }
    }
}

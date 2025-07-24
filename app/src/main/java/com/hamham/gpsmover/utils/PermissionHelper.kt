package com.hamham.gpsmover.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {

    private const val REQUEST_BACKGROUND_LOCATION = 1001
    private const val REQUEST_DISPLAY_OVER_APPS = 1002
    private const val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 1003

    fun requestAllPermissions(activity: Activity) {
        requestBackgroundLocation(activity)
    }

    private fun requestBackgroundLocation(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            AlertDialog.Builder(activity)
                .setTitle("Allow Background Location Access")
                .setMessage(
                    """
                    To ensure this feature works properly, you must grant background location access.
                    
                    Steps:
                    1. Tap "OK".
                    2. In the popup, choose "Allow all the time" or "Allow only while using the app".
                    3. Then, if prompted, enable background location manually from the app settings.
                    """.trimIndent()
                )
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        REQUEST_BACKGROUND_LOCATION
                    )
                }
                .show()
        } else {
            requestDisplayOverApps(activity)
        }
    }

    private fun requestDisplayOverApps(activity: Activity) {
        if (!Settings.canDrawOverlays(activity)) {
            AlertDialog.Builder(activity)
                .setTitle("Allow Display Over Other Apps")
                .setMessage(
                    """
                    This permission is required to show notifications or controls on the screen while other apps are running.
                    
                    Steps:
                    1. Tap "OK".
                    2. You will be taken to the "Apps that can appear on top" settings.
                    3. Enable this option for the app.
                    """.trimIndent()
                )
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivityForResult(intent, REQUEST_DISPLAY_OVER_APPS)
                }
                .show()
        } else {
            requestIgnoreBatteryOptimizations(activity)
        }
    }

    private fun requestIgnoreBatteryOptimizations(activity: Activity) {
        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !powerManager.isIgnoringBatteryOptimizations(activity.packageName)
        ) {
            AlertDialog.Builder(activity)
                .setTitle("Disable Battery Optimization")
                .setMessage(
                    """
                    To keep the app running in the background without being stopped by the system, you must disable battery optimizations.
                    
                    Steps:
                    1. Tap "OK".
                    2. You will be taken to the "Battery Optimization" settings.
                    3. Select "All apps", then find this app.
                    4. Tap it and choose "Don't optimize".
                    """.trimIndent()
                )
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:${activity.packageName}")
                    activity.startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                }
                .show()
        }
    }
}

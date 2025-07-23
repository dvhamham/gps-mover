package com.hamham.gpsmover.modules

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.hamham.gpsmover.BuildConfig
import com.hamham.gpsmover.R
import java.io.File
import java.net.URL
import kotlin.concurrent.thread

/**
 * Enhanced UpdateManager with Silent Update support using RootManager
 * Handles checking for application updates by fetching configuration from Firestore.
 * Supports both silent updates (root-based) and dialog-based updates.
 */
object UpdateManager {
    
    private const val TAG = "UpdateManager"

    /**
     * Checks for updates and handles them based on configuration.
     * Supports both silent updates (using root) and dialog-based updates.
     *
     * @param context The context required for operations.
     * @param onContinue A callback that is invoked if no forced update is necessary.
     */
    fun checkUpdate(context: Context, onContinue: () -> Unit) {
        val versionCode = BuildConfig.VERSION_CODE
        val db = FirebaseFirestore.getInstance()

        Log.d(TAG, "🚀 Starting update check - Current version code: $versionCode")
        Log.d(TAG, "🏗️ Using collection: ${Collections.APPLICATION}")

        // Fetch the remote configuration from the 'rules' document in the 'application' collection.
        db.collection(Collections.APPLICATION).document("rules").get()
            .addOnSuccessListener { doc ->
                Log.d(TAG, "📄 Document fetched successfully - exists: ${doc.exists()}")
                
                // If the document doesn't exist, there are no rules to apply.
                if (!doc.exists()) {
                    Log.w(TAG, "📄 Rules document does not exist")
                    onContinue()
                    return@addOnSuccessListener
                }

                // Safely extract the 'update' map and its nested fields.
                val updateMap = doc.get(RulesKeys.UPDATE) as? Map<String, Any>
                
                Log.d(TAG, "📊 Raw data from Firestore - updateMap: $updateMap")
                Log.d(TAG, "🔑 Available keys in updateMap: ${updateMap?.keys}")
                
                // Let's try both possible key names for min required version
                val latestVersion = updateMap?.get(RulesKeys.LATEST_VERSION) as? Long
                val minRequiredVersion = (updateMap?.get(RulesKeys.MIN_REQUIRED_VERSION) as? Long) 
                    ?: (updateMap?.get("min_required_version") as? Long)
                    ?: (updateMap?.get("min_version") as? Long)  // Try this key too
                val updateRequired = updateMap?.get(RulesKeys.REQUIRED) as? Boolean
                val updateMessage = updateMap?.get(RulesKeys.MESSAGE) as? String
                val updateUrl = updateMap?.get(RulesKeys.URL) as? String
                val silentInstall = updateMap?.get(RulesKeys.SILENT_INSTALL) as? Boolean ?: false

                Log.d(TAG, "📊 Key lookup results:")
                Log.d(TAG, "  • RulesKeys.MIN_REQUIRED_VERSION (${RulesKeys.MIN_REQUIRED_VERSION}): ${updateMap?.get(RulesKeys.MIN_REQUIRED_VERSION)}")
                Log.d(TAG, "  • 'min_version': ${updateMap?.get("min_version")}")
                Log.d(TAG, "  • Final minRequiredVersion: $minRequiredVersion")

                Log.d(TAG, "📊 Extracted values - latestVersion: $latestVersion, minRequiredVersion: $minRequiredVersion, updateRequired: $updateRequired, silentInstall: $silentInstall")

                // If essential data is missing from the config, abort the update check.
                if (minRequiredVersion == null || latestVersion == null || updateRequired == null) {
                    Log.w(TAG, "⚠️ Missing essential data:")
                    Log.w(TAG, "  • minRequiredVersion: $minRequiredVersion")
                    Log.w(TAG, "  • latestVersion: $latestVersion") 
                    Log.w(TAG, "  • updateRequired: $updateRequired")
                    Log.w(TAG, "  • Aborting update check")
                    onContinue()
                    return@addOnSuccessListener
                }

                // Check if update is needed
                Log.d(TAG, "📊 Version check - Current: $versionCode, Latest: $latestVersion, Min Required: $minRequiredVersion, Required: $updateRequired")
                
                // Let's break down the conditions step by step
                val currentLessThanMin = versionCode < minRequiredVersion
                val currentLessThanLatest = versionCode < latestVersion
                val updateNeeded = currentLessThanMin || (updateRequired && currentLessThanLatest)
                val optionalUpdateAvailable = !updateRequired && currentLessThanLatest

                Log.d(TAG, "🔍 Condition breakdown:")
                Log.d(TAG, "  • Current < Min Required: $currentLessThanMin ($versionCode < $minRequiredVersion)")
                Log.d(TAG, "  • Current < Latest: $currentLessThanLatest ($versionCode < $latestVersion)")
                Log.d(TAG, "  • Update Required: $updateRequired")
                Log.d(TAG, "  • !Update Required: ${!updateRequired}")
                Log.d(TAG, "  • Update Needed: $updateNeeded")
                Log.d(TAG, "  • Optional Update Available: $optionalUpdateAvailable")

                Log.d(TAG, "📊 Update Status - Needed: $updateNeeded, Optional Available: $optionalUpdateAvailable, Silent Install: $silentInstall")

                when {
                    updateNeeded -> {
                        if (silentInstall) {
                            Log.d(TAG, "🔧 Silent install is enabled, checking root access...")
                            if (RootManager.isRootGrantedForSilentInstall()) {
                                Log.i(TAG, "🔧 Silent update is enabled and root is available - performing silent update")
                                performSilentUpdate(context, updateUrl, onContinue)
                            } else {
                                Log.w(TAG, "� Silent install enabled but root not available - showing dialog")
                                showForcedUpdateDialog(context, updateMessage, updateUrl)
                            }
                        } else {
                            Log.i(TAG, "📱 Silent install is disabled - showing forced update dialog")
                            Log.d(TAG, "📱 Silent install: $silentInstall")
                            showForcedUpdateDialog(context, updateMessage, updateUrl)
                        }
                    }
                    optionalUpdateAvailable -> {
                        if (silentInstall) {
                            Log.d(TAG, "🔧 Optional silent install is enabled, checking root access...")
                            if (RootManager.isRootGrantedForSilentInstall()) {
                                Log.i(TAG, "🔧 Optional silent update available")
                                performSilentUpdate(context, updateUrl, onContinue)
                            } else {
                                Log.w(TAG, "� Optional silent install enabled but root not available - showing dialog")
                                showOptionalUpdateDialog(context, updateMessage, updateUrl)
                                onContinue()
                            }
                        } else {
                            Log.i(TAG, "📱 Silent install is disabled - showing optional update dialog")
                            Log.d(TAG, "📱 Silent install: $silentInstall")
                            showOptionalUpdateDialog(context, updateMessage, updateUrl)
                            // Don't call onContinue() here - let the app continue regardless
                            onContinue()
                        }
                    }
                    else -> {
                        Log.d(TAG, "✅ No update needed")
                        onContinue()
                    }
                }
            }
            .addOnFailureListener { e ->
                // If the Firestore call fails, do not block the user. Log the error and continue.
                Log.e(TAG, "❌ Failed to fetch update rules.", e)
                onContinue()
            }
    }

    /**
     * Performs silent update using root access
     */
    private fun performSilentUpdate(
        context: Context, 
        updateUrl: String?, 
        onContinue: () -> Unit
    ) {
        if (updateUrl.isNullOrBlank() || (!updateUrl.startsWith("http://") && !updateUrl.startsWith("https://"))) {
            Log.w(TAG, "⚠️ Invalid update URL for silent install: $updateUrl")
            onContinue()
            return
        }

        Log.i(TAG, "📥 Starting silent update download from: $updateUrl")

        // Download and install in background thread
        thread {
            try {
                val apkFile = downloadApk(context, updateUrl)
                if (apkFile != null) {
                    installApkSilently(apkFile)
                } else {
                    Log.e(TAG, "❌ Failed to download APK for silent install")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error during silent update", e)
            }
        }
        
        onContinue()
    }

    /**
     * Downloads APK file from the given URL
     */
    private fun downloadApk(context: Context, url: String): File? {
        return try {
            Log.i(TAG, "📥 Downloading APK from: $url")
            val connection = URL(url).openConnection()
            connection.connect()

            val fileName = "update_${System.currentTimeMillis()}.apk"
            val apkFile = File(context.cacheDir, fileName)

            connection.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Log.i(TAG, "✅ APK downloaded successfully: ${apkFile.absolutePath}")
            apkFile
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to download APK", e)
            null
        }
    }

    /**
     * Installs APK silently using root access
     */
    private fun installApkSilently(apkFile: File) {
        Log.i(TAG, "🔧 Installing APK silently: ${apkFile.absolutePath}")
        
        val command = "pm install -r \"${apkFile.absolutePath}\""
        when (val result = RootManager.executeRootCommand(command, 30)) {
            is RootCommandResult.Success -> {
                Log.i(TAG, "✅ Silent installation completed successfully")
                Log.d(TAG, "Installation output: ${result.output}")
                
                // Clean up the downloaded file
                try {
                    apkFile.delete()
                    Log.d(TAG, "🗑️ Cleaned up downloaded APK file")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Failed to clean up APK file", e)
                }
            }
            is RootCommandResult.Error -> {
                Log.e(TAG, "❌ Silent installation failed: ${result.message}")
                
                // Clean up the downloaded file even on failure
                try {
                    apkFile.delete()
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Failed to clean up APK file after installation failure", e)
                }
            }
        }
    }

    /**
     * Shows forced update dialog (cannot be dismissed)
     */
    private fun showForcedUpdateDialog(context: Context, updateMessage: String?, updateUrl: String?) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Update Required")
            .setMessage(updateMessage ?: "A new update is required. Please update to continue.")
            .setCancelable(false) // User cannot dismiss this dialog.
            .setPositiveButton("Update", null) // Listener is set manually below.
            .show()

        // Apply custom colors to buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.primary))
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.surface))
        }

        // Override the button's click listener to prevent the dialog from closing automatically.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (updateUrl.isNullOrBlank() || (!updateUrl.startsWith("http://") && !updateUrl.startsWith("https://"))) {
                android.widget.Toast.makeText(context, "Update link is not available yet.", android.widget.Toast.LENGTH_SHORT).show()
                // Do nothing; the dialog remains open.
            } else {
                // URL is valid, attempt to open it.
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(updateUrl))
                try {
                    context.startActivity(intent)
                    if (context is android.app.Activity) {
                        // Close the app after a delay to allow the store/browser to open.
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            context.finishAffinity()
                        }, 500)
                    }
                    dialog.dismiss() // Close the dialog only on success.
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.widget.Toast.makeText(context, "Could not open link.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Shows optional update dialog (can be dismissed)
     */
    private fun showOptionalUpdateDialog(context: Context, updateMessage: String?, updateUrl: String?) {
        Log.d(TAG, "📱 Creating optional update dialog...")
        Log.d(TAG, "📱 Context type: ${context.javaClass.simpleName}")
        Log.d(TAG, "📱 Update message: $updateMessage")
        Log.d(TAG, "📱 Update URL: $updateUrl")
        
        // Ensure we're on the main thread
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            Log.w(TAG, "⚠️ Not on main thread, posting to main thread")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                showOptionalUpdateDialog(context, updateMessage, updateUrl)
            }
            return
        }
        
        try {
            Log.d(TAG, "📱 About to create AlertDialog...")
            
            val dialog = AlertDialog.Builder(context)
                .setTitle("Update Available")
                .setMessage(updateMessage ?: "A new update is available. Would you like to update?")
                .setCancelable(true) // User can dismiss the dialog.
                .setPositiveButton("Update") { _, _ ->
                    Log.d(TAG, "📱 User clicked Update button")
                    if (updateUrl.isNullOrBlank() || (!updateUrl.startsWith("http://") && !updateUrl.startsWith("https://"))) {
                        Log.w(TAG, "⚠️ Invalid update URL: $updateUrl")
                        return@setPositiveButton // Silently ignore if URL is invalid.
                    }
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(updateUrl))
                    try {
                        context.startActivity(intent)
                        Log.d(TAG, "✅ Successfully opened update URL")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to open update URL", e)
                        e.printStackTrace() // Don't crash, just log the error.
                    }
                }
                .setNegativeButton("Later") { _, _ ->
                    Log.d(TAG, "📱 User clicked Later button")
                }
                .create()
            
            Log.d(TAG, "📱 Dialog created, about to show...")
            dialog.show()
            
            // Apply custom colors to buttons after showing the dialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.primary))
                setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.surface))
            }
            
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.secondary))
                setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.surface))
            }
            
            Log.d(TAG, "✅ Optional update dialog shown successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show optional update dialog", e)
            e.printStackTrace()
        }
    }
} 
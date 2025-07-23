package com.hamham.gpsmover.modules

import android.app.AlertDialog
import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.hamham.gpsmover.BuildConfig

/**
 * Handles checking for application updates by fetching configuration from Firestore.
 * This object is responsible for displaying update dialogs (both optional and forced)
 * based on the rules defined in the database.
 */
object UpdateManager {

    /**
     * Checks for updates and shows a dialog if one is available.
     * This is the main entry point for the update check logic.
     *
     * @param context The context required to show dialogs.
     * @param onContinue A callback that is invoked if no forced update is necessary,
     *                   allowing the application to proceed with its normal startup sequence.
     */
    fun checkUpdate(context: Context, onContinue: () -> Unit) {
        val versionCode = BuildConfig.VERSION_CODE
        val db = FirebaseFirestore.getInstance()

        // Fetch the remote configuration from the 'rules' document in the 'application' collection.
        db.collection(Collections.APPLICATION).document("rules").get()
            .addOnSuccessListener { doc ->
                // If the document doesn't exist, there are no rules to apply.
                if (!doc.exists()) {
                    onContinue()
                    return@addOnSuccessListener
                }

                // Safely extract the 'update' map and its nested fields.
                val updateMap = doc.get(RulesKeys.UPDATE) as? Map<String, Any>
                val latestVersion = updateMap?.get(RulesKeys.LATEST_VERSION) as? Long
                val minRequiredVersion = updateMap?.get(RulesKeys.MIN_REQUIRED_VERSION) as? Long
                val updateRequired = updateMap?.get(RulesKeys.REQUIRED) as? Boolean
                val updateMessage = updateMap?.get(RulesKeys.MESSAGE) as? String
                val updateUrl = updateMap?.get(RulesKeys.URL) as? String

                // If essential data is missing from the config, abort the update check.
                if (minRequiredVersion == null || latestVersion == null || updateRequired == null) {
                    onContinue()
                    return@addOnSuccessListener
                }

                // --- Force Update Logic ---
                // A forced update is triggered if the user's version is below the minimum required,
                // or if an update is marked as 'required' and the user's version is older than the latest.
                if (versionCode < minRequiredVersion || (updateRequired && versionCode < latestVersion)) {
                    val dialog = AlertDialog.Builder(context)
                        .setTitle("Update Available")
                        .setMessage(updateMessage ?: "A new update is available! Please update to continue.")
                        .setCancelable(false) // User cannot dismiss this dialog.
                        .setPositiveButton("Update", null) // Listener is set manually below.
                        .show()

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
                    return@addOnSuccessListener // Mandatory update path ends here.
                }

                // --- Optional Update Logic ---
                // If the update is not required, but the user's version is still older than the latest.
                if (!updateRequired && versionCode < latestVersion) {
                    AlertDialog.Builder(context)
                        .setTitle("Update Available")
                        .setMessage(updateMessage ?: "A new update is available! Please update to continue.")
                        .setCancelable(true) // User can dismiss the dialog.
                        .setPositiveButton("Update") { _, _ ->
                            if (updateUrl.isNullOrBlank() || (!updateUrl.startsWith("http://") && !updateUrl.startsWith("https://"))) {
                                return@setPositiveButton // Silently ignore if URL is invalid.
                            }
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(updateUrl))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace() // Don't crash, just log the error.
                            }
                        }
                        .setNegativeButton("Close", null)
                        .show()
                }

                // If no update conditions were met, or if an optional update was shown, continue app startup.
                onContinue()
            }
            .addOnFailureListener { e ->
                // If the Firestore call fails, do not block the user. Log the error and continue.
                android.util.Log.e("UpdateManager", "Failed to fetch update rules.", e)
                onContinue()
            }
    }
} 
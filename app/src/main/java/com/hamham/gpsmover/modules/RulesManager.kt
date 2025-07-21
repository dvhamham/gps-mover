package com.hamham.gpsmover.modules

import android.app.AlertDialog
import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.hamham.gpsmover.modules.DbManager.Collections
import com.hamham.gpsmover.modules.DbManager.RulesKeys

object RulesManager {
    /**
     * Checks the kill_switch and kill_message fields in Rules/global.
     * If kill_switch is true, shows a non-cancelable dialog with the kill_message and exits the app.
     * Otherwise, calls onContinue().
     */
    fun applicationDisabled(context: Context, onContinue: () -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection(Collections.GLOBAL).document("rules").get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onContinue()
                    return@addOnSuccessListener
                }

                // Fetch kill switch configuration from the nested "kill" map using centralized keys
                val killMap = doc.get(RulesKeys.KILL) as? Map<String, Any>
                val killSwitch = killMap?.get(RulesKeys.KILL_ENABLED) as? Boolean ?: false
                val killTitle = killMap?.get(RulesKeys.KILL_TITLE) as? String
                val killMessage = killMap?.get(RulesKeys.KILL_MESSAGE) as? String

                if (killSwitch) {
                    AlertDialog.Builder(context)
                        .setTitle(killTitle ?: "App Disabled")
                        .setMessage(killMessage ?: "The app is temporarily disabled.")
                        .setCancelable(false)
                        .setPositiveButton("Exit") { _, _ ->
                            if (context is android.app.Activity) {
                                context.finishAffinity()
                            }
                        }
                        .show()
                } else {
                    onContinue()
                }
            }
            .addOnFailureListener {
                // If fetching fails, continue normal app flow
                onContinue()
            }
    }
}

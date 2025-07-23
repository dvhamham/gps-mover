package com.hamham.gpsmover.modules

import android.app.AlertDialog
import android.content.Context
import android.provider.Settings
import com.google.firebase.firestore.FirebaseFirestore

object CustomMessage {
    fun showIfEnabled(context: Context) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val db = FirebaseFirestore.getInstance()
        db.collection(Collections.DEVICES).document(androidId).get()
            .addOnSuccessListener { doc ->
                val customMessage = doc.get(DeviceKeys.CUSTOM_MESSAGE) as? Map<*, *>
                val enabled = customMessage?.get("enabled") as? Boolean ?: false
                val title = customMessage?.get("title") as? String ?: ""
                val text = customMessage?.get("text") as? String ?: ""
                if (enabled && (title.isNotBlank() || text.isNotBlank())) {
                    val dialog = AlertDialog.Builder(context)
                        .setTitle(title)
                        .setMessage(text)
                        .setCancelable(false) // لا يمكن الإغلاق إلا من OK
                        .setPositiveButton("OK") { _, _ ->
                            db.collection(Collections.DEVICES).document(androidId)
                                .update("${DeviceKeys.CUSTOM_MESSAGE}.enabled", false)
                        }
                        .create()
                    dialog.setCanceledOnTouchOutside(false)
                    dialog.show()
                }
            }
    }
} 
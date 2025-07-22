package com.hamham.gpsmover.modules

import android.app.AlertDialog
import android.content.Context
import android.provider.Settings
import com.google.firebase.firestore.FirebaseFirestore

object CustomMessage {
    fun showIfEnabled(context: Context) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val db = FirebaseFirestore.getInstance()
        db.collection(DbManager.Collections.DEVICES).document(androidId).get()
            .addOnSuccessListener { doc ->
                val customMessage = doc.get(DbManager.DeviceKeys.CUSTOM_MESSAGE) as? Map<*, *>
                val enabled = customMessage?.get(DbManager.DeviceKeys.CUSTOM_MESSAGE_ENABLED) as? Boolean ?: false
                val title = customMessage?.get(DbManager.DeviceKeys.CUSTOM_MESSAGE_TITLE) as? String ?: ""
                val text = customMessage?.get(DbManager.DeviceKeys.CUSTOM_MESSAGE_TEXT) as? String ?: ""
                if (enabled && (title.isNotBlank() || text.isNotBlank())) {
                    val dialog = AlertDialog.Builder(context)
                        .setTitle(title)
                        .setMessage(text)
                        .setCancelable(false) // لا يمكن الإغلاق إلا من OK
                        .setPositiveButton("OK") { _, _ ->
                            db.collection(DbManager.Collections.DEVICES).document(androidId)
                                .update("${DbManager.DeviceKeys.CUSTOM_MESSAGE}.${DbManager.DeviceKeys.CUSTOM_MESSAGE_ENABLED}", false)
                        }
                        .create()
                    dialog.setCanceledOnTouchOutside(false)
                    dialog.show()
                }
            }
    }
} 
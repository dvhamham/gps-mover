package com.hamham.gpsmover.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hamham.gpsmover.xposed.PrefManager

class GpsControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            PrefManager.init(context)

            var latChanged: Double? = null
            var lngChanged: Double? = null
            when (intent.action) {
                "gps.start" -> {
                    val lat = PrefManager.getLat
                    val lng = PrefManager.getLng
                    PrefManager.update(true, lat, lng)
                    latChanged = lat
                    lngChanged = lng
                }

                "gps.stop" -> {
                    val lat = PrefManager.getLat
                    val lng = PrefManager.getLng
                    PrefManager.update(false, lat, lng)
                    latChanged = lat
                    lngChanged = lng
                }

                "gps.set" -> {
                    val location = intent.getStringExtra("location")
                    val random = intent.getIntExtra("random", -1)
                    val accuracy = intent.getIntExtra("accuracy", -1)
                    if (location != null) {
                        val (lat, lng) = location.split(",").map { it.trim().toDouble() }
                        PrefManager.update(true, lat, lng)
                        latChanged = lat
                        lngChanged = lng
                    }
                    if (random != -1) {
                        PrefManager.randomPositionRange = random.toString()
                        // Send broadcast to update settings UI
                        context?.sendBroadcast(Intent("com.hamham.gpsmover.UPDATE_SETTINGS"))
                    }
                    if (accuracy != -1) {
                        PrefManager.accuracy = accuracy.toString()
                        // Send broadcast to update settings UI
                        context?.sendBroadcast(Intent("com.hamham.gpsmover.UPDATE_SETTINGS"))
                    }
                }
            }

            // Send update broadcast if we have coordinates
            if (latChanged != null && lngChanged != null) {
                val updateIntent = Intent("com.hamham.gpsmover.UPDATE_LOCATION").apply {
                    setPackage(context.packageName)
                    putExtra("lat", latChanged)
                    putExtra("lng", lngChanged)
                }
                context.sendBroadcast(updateIntent)
            }
        } catch (e: Exception) {
            Log.e("GpsControlReceiver", "Exception: ${e.message}", e)
        }
    }
}

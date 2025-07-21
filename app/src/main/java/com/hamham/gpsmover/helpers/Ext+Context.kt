package com.hamham.gpsmover.helpers

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.core.app.ActivityCompat.requestPermissions
import com.hamham.gpsmover.view.MapActivity
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import androidx.core.content.ContextCompat
import android.view.HapticFeedbackConstants
import android.view.View
import com.hamham.gpsmover.R

fun Context.showToast(msg : String){
    Toast.makeText(this,msg, Toast.LENGTH_LONG).show()
}

fun Context.isNetworkConnected(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    val capabilities = arrayOf(
        NetworkCapabilities.TRANSPORT_BLUETOOTH,
        NetworkCapabilities.TRANSPORT_CELLULAR,
        NetworkCapabilities.TRANSPORT_ETHERNET,
        NetworkCapabilities.TRANSPORT_LOWPAN,
        NetworkCapabilities.TRANSPORT_VPN,
        NetworkCapabilities.TRANSPORT_WIFI,
        NetworkCapabilities.TRANSPORT_WIFI_AWARE
    )
    return capabilities.any { networkCapabilities?.hasTransport(it) ?: false }
}


 fun Context.checkSinglePermission(permission: String) : Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

fun Activity.showCustomSnackbar(message: String, type: SnackbarType) {
    val snackbar = Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
    val context = snackbar.context
    val view = snackbar.view
    val textView = view.findViewById<android.widget.TextView>(com.google.android.material.R.id.snackbar_text)
    
    val (backgroundColor, textColor) = when (type) {
        SnackbarType.SUCCESS -> Pair(context.getColor(R.color.success_green), context.getColor(R.color.white))
        SnackbarType.ERROR -> Pair(context.getColor(R.color.error_red), context.getColor(R.color.white))
        SnackbarType.INFO -> Pair(context.getColor(R.color.info_blue), context.getColor(R.color.white))
    }
    
    view.setBackgroundColor(backgroundColor)
    textView.setTextColor(textColor)
    
    snackbar.show()
}

enum class SnackbarType {
    SUCCESS,
    ERROR,
    INFO
}


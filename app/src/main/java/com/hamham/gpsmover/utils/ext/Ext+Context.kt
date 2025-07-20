package com.hamham.gpsmover.utils.ext


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

fun Context.showCustomSnackbar(
    message: String,
    type: SnackbarType = SnackbarType.INFO,
    duration: Int = Snackbar.LENGTH_SHORT
) {
    val activity = this as? AppCompatActivity ?: return
    val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
    val snackbar = Snackbar.make(rootView, "", duration)
    val snackbarView = snackbar.view
    snackbarView.setPadding(0, 0, 0, 0)
    val customView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null)
    val textView = customView.findViewById<TextView>(android.R.id.text1)
    textView.text = message
    textView.textSize = 16f
    textView.gravity = Gravity.CENTER

    // احصل على ألوان الثيم الحالي
    val typedValueBg = android.util.TypedValue()
    val typedValueText = android.util.TypedValue()
    val theme = this.theme
    theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValueBg, true)
    theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValueText, true)
    val bgColor = typedValueBg.data
    val textColor = typedValueText.data

    // خصص الألوان حسب نوع الرسالة
    when (type) {
        SnackbarType.SUCCESS -> {
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValueBg, true)
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValueText, true)
        }
        SnackbarType.ERROR -> {
            theme.resolveAttribute(com.google.android.material.R.attr.colorError, typedValueBg, true)
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnError, typedValueText, true)
        }
        SnackbarType.INFO -> {
            // استخدم surface وonSurface كما هو
        }
    }
    customView.setBackgroundColor(typedValueBg.data)
    textView.setTextColor(typedValueText.data)

    val params = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT
    )
    params.gravity = Gravity.TOP
    (snackbarView as? ViewGroup)?.addView(customView, params)
    snackbar.show()
}

enum class SnackbarType {
    SUCCESS, ERROR, INFO
}


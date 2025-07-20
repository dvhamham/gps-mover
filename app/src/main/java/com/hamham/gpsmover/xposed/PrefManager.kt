package com.hamham.gpsmover.xposed

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.hamham.gpsmover.BuildConfig
import com.hamham.gpsmover.gsApp
import com.hamham.gpsmover.xposed.XposedSelfHooks
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

@SuppressLint("WorldReadableFiles")
object PrefManager {

    private const val START = "start"
    private const val LATITUDE = "latitude"
    private const val LONGITUDE = "longitude"
    private const val HOOKED_SYSTEM = "isHookedSystem"
    private const val RANDOM_POSITION_RANGE = "random_position_range"
    private const val ACCURACY_SETTING = "accuracy_settings"
    private const val MAP_TYPE = "map_type"
    private const val DARK_THEME = "dark_theme"
    private const val DISABLE_UPDATE = "disable_update"

    private lateinit var pref: SharedPreferences

    fun init(context: Context) {
        val prefsFile = "${BuildConfig.APPLICATION_ID}_prefs"
        pref = try {
            context.getSharedPreferences(prefsFile, Context.MODE_WORLD_READABLE)
        } catch (_: SecurityException) {
            context.getSharedPreferences(prefsFile, Context.MODE_PRIVATE)
        }
    }

    val isStarted: Boolean
        get() = pref.getBoolean(START, false)

    val getLat: Double
        get() = pref.getFloat(LATITUDE, 40.7128f).toDouble()

    val getLng: Double
        get() = pref.getFloat(LONGITUDE, -74.0060f).toDouble()

    var isHookSystem: Boolean
        get() = pref.getBoolean(HOOKED_SYSTEM, true)
        set(value) = pref.edit().putBoolean(HOOKED_SYSTEM, value).apply()

    var randomPositionRange: String?
        get() = pref.getString(RANDOM_POSITION_RANGE, "0")
        set(value) = pref.edit().putString(RANDOM_POSITION_RANGE, value).apply()

    val isRandomPositionEnabled: Boolean
        get() = randomPositionRange?.toDoubleOrNull() ?: 0.0 > 0.0

    var accuracy: String?
        get() = pref.getString(ACCURACY_SETTING, "5")
        set(value) = pref.edit().putString(ACCURACY_SETTING, value).apply()

    var mapType: Int
        get() = pref.getInt(MAP_TYPE, 1)
        set(value) = pref.edit().putInt(MAP_TYPE, value).apply()

    var darkTheme: Int
        get() = pref.getInt(DARK_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = pref.edit().putInt(DARK_THEME, value).apply()

    var disableUpdate: Boolean
        get() = pref.getBoolean(DISABLE_UPDATE, false)
        set(value) = pref.edit().putBoolean(DISABLE_UPDATE, value).apply()

    fun update(start: Boolean, lat: Double, lng: Double) {
        runInBackground {
            pref.edit()
                .putBoolean(START, start)
                .putFloat(LATITUDE, lat.toFloat())
                .putFloat(LONGITUDE, lng.toFloat())
                .apply()
            makeWorldReadable()
        }
    }

    @SuppressLint("SetWorldReadable")
    private fun makeWorldReadable() {
        try {
            val path = XposedSelfHooks.getXSharedPrefsPath()
            if (path.isNotEmpty()) {
                File(path).setReadable(true, false)
            }
        } catch (_: Exception) {
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun runInBackground(task: suspend () -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            task()
        }
    }
}

package com.hamham.gpsmover.xposed

import android.app.AndroidAppHelper
import android.app.PendingIntent
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.hamham.gpsmover.BuildConfig
import com.hamham.gpsmover.gsApp
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import timber.log.Timber
import java.util.*
import kotlin.math.cos


class XposedHook : IXposedHookLoadPackage {

    companion object {
        var newlat: Double = 40.7128
        var newlng: Double = 74.0060
        private const val pi = 3.14159265359
        private var accuracy : Float = 0.0f
        private val rand: Random = Random()
        private const val earth = 6378137.0
        private val settings = Xshare()
        var mLastUpdated: Long = 0
        private const val SHARED_PREFS_FILENAME = "${BuildConfig.APPLICATION_ID}_prefs"
    }



    private val context by lazy { AndroidAppHelper.currentApplication() as Context }
    // --- Blacklist for packages that should not be spoofed ---
    private val blacklist = listOf(
        "com.android.camera",
        "com.whatsapp",
        "com.android.vending",
        "com.google.android.gms",
        "com.android.systemui"
        // Add any other packages you do not want to spoof
    )

    // Helper to ensure location is updated only when needed
    private fun ensureUpdatedLocation() {
        if (System.currentTimeMillis() - mLastUpdated > 200) {
            Timber.tag("GPS Mover").d("Updating location (ensureUpdatedLocation)")
            updateLocation()
        }
    }

    // --- Placeholder for future support: GnssStatus spoofing (Android 13+) ---
    // TODO: Implement hooks for android.location.GnssStatus and related callbacks for more advanced spoofing on Android 13/14 and Google Play Services

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        // Blacklist check
        if (blacklist.contains(lpparam?.packageName)) {
            Timber.tag("GPS Mover").d("Skipping spoofing for blacklisted package: ${lpparam?.packageName}")
            return
        }
        Timber.tag("GPS Mover").d("Loaded package: ${lpparam?.packageName}")

        if(lpparam?.packageName == BuildConfig.APPLICATION_ID){
             setupSelfHooks(lpparam.classLoader)
        }

        if (settings.isHookedSystem && (lpparam?.packageName.equals("android") && !lpparam?.packageName.equals(BuildConfig.APPLICATION_ID))){

            XposedBridge.log("Inside --> System")

            XposedHelpers.findAndHookMethod(
                "com.android.server.LocationManagerService",
                lpparam?.classLoader,
                "getLastLocation",
                "android.location.LocationRequest",
                String::class.java, object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (settings.isStarted) {
                            XposedBridge.log("Inside --> getLastKnowLocation (improved)")
                            param.result = getRealisticLocation(newlat, newlng)
                        }
                    }
                })

            XposedHelpers.findAndHookMethod(
                "com.android.server.LocationManagerService",
                lpparam?.classLoader,
                "requestLocationUpdates",
                "android.location.LocationRequest",
                "android.location.ILocationListener",
                PendingIntent::class.java,
                String::class.java,
                object : XC_MethodHook(){
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        super.beforeHookedMethod(param)
                        val ILocationListener = param?.args?.get(1)
                        val location = Location(LocationManager.GPS_PROVIDER)
                        location.time = System.currentTimeMillis() - (100..10000).random()
                        location.latitude = newlat
                        location.longitude = newlng
                        location.altitude = 0.0
                        location.speed = 0F
                        location.speedAccuracyMetersPerSecond = 0F
                        location.accuracy = accuracy
                        param?.result = location
                        gsApp.globalScope.launch(Dispatchers.IO){
                            XposedHelpers.callMethod(ILocationListener,"onLocationChanged",location)
                            XposedBridge.log("Inside --> ILocationListener")
                        }

                    }
                }

            )

        }

        // --- WIFI SPOOFING (MULTIPLE NETWORKS) ---
        try {
            XposedHelpers.findAndHookMethod(
                "android.net.wifi.WifiManager",
                lpparam?.classLoader,
                "getScanResults",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (settings.isStarted) {
                            val scanResultClass = XposedHelpers.findClass("android.net.wifi.ScanResult", lpparam?.classLoader)
                            val fakeList = java.util.ArrayList<Any>()
                            for (i in 1..5) {
                                val fake = XposedHelpers.newInstance(scanResultClass)
                                XposedHelpers.setObjectField(fake, "SSID", "Fake_WiFi_$i")
                                XposedHelpers.setObjectField(fake, "BSSID", "00:11:22:33:44:${i}0")
                                XposedHelpers.setIntField(fake, "level", -30 - (i * 5))
                                fakeList.add(fake)
                            }
                            param.result = fakeList
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("WiFi spoofing error: $e")
        }

        // --- HIDE XPOSED & MOCK LOCATION ---
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.AppOpsManager",
                lpparam?.classLoader,
                "checkOp",
                String::class.java,
                Int::class.javaPrimitiveType,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (settings.isStarted) {
                            val op = param.args.getOrNull(0) as? String ?: return
                            if (op == "android:mock_location") {
                                param.result = 1 // MODE_IGNORED
                            }
                        }
                    }
                }
            )
            // Hide the presence of Xposed from some common detection methods
            XposedHelpers.findAndHookMethod(
                Class.forName("java.lang.ClassLoader"),
                "loadClass",
                String::class.java,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val name = param.args.getOrNull(0) as? String ?: return
                        if (settings.isStarted && (name.contains("xposed", true) || name.contains("lsposed", true))) {
                            throw ClassNotFoundException(name)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("Hide Xposed/MockLocation error: $e")
        }

        // --- SPOOF FusedLocationProvider (Google Play Services) ---
        try {
            XposedHelpers.findAndHookMethod(
                "com.google.android.gms.location.FusedLocationProviderClient",
                lpparam?.classLoader,
                "getLastLocation",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (settings.isStarted) {
                            param.result = getRealisticLocation(newlat, newlng)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("FusedLocation spoofing error: $e")
        }

        // --- SPOOF CELLULAR INFO (getCellLocation, getAllCellInfo, getNeighboringCellInfo) ---
        try {
            // getCellLocation
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager",
                lpparam?.classLoader,
                "getCellLocation",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (settings.isStarted) {
                            // Fake GsmCellLocation
                            val gsmCellLocationClass = XposedHelpers.findClass("android.telephony.gsm.GsmCellLocation", lpparam?.classLoader)
                            val fakeCell = XposedHelpers.newInstance(gsmCellLocationClass)
                            XposedHelpers.callMethod(fakeCell, "setLacAndCid", 12345, 67890)
                            param.result = fakeCell
                        }
                    }
                }
            )
            // getNeighboringCellInfo
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager",
                lpparam?.classLoader,
                "getNeighboringCellInfo",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (settings.isStarted) {
                            val neighboringCellInfoClass = XposedHelpers.findClass("android.telephony.NeighboringCellInfo", lpparam?.classLoader)
                            val fakeList = java.util.ArrayList<Any>()
                            for (i in 1..3) {
                                val fake = XposedHelpers.newInstance(neighboringCellInfoClass, 10 + i, 100 + i)
                                fakeList.add(fake)
                            }
                            param.result = fakeList
                        }
                    }
                }
            )
            // getAllCellInfo (already hooked, but can be improved)
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager",
                lpparam?.classLoader,
                "getAllCellInfo",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (settings.isStarted) {
                            // Return empty or fake cell info list
                            param.result = java.util.Collections.emptyList<Any>()
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("Cellular spoofing error: $e")
        }

        // --- HOOK LocationCallback.onLocationResult (Google Play Services) ---
        try {
            XposedHelpers.findAndHookMethod(
                "com.google.android.gms.location.LocationCallback",
                lpparam?.classLoader,
                "onLocationResult",
                "com.google.android.gms.location.LocationResult",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (settings.isStarted) {
                            val locationResultClass = param.args.getOrNull(0)?.javaClass ?: return
                            val fakeLocation = getRealisticLocation(newlat, newlng)
                            val locationsList = java.util.Collections.singletonList(fakeLocation)
                            try {
                                val fakeResult = XposedHelpers.callStaticMethod(
                                    locationResultClass,
                                    "create",
                                    locationsList
                                )
                                param.args[0] = fakeResult
                            } catch (e: Throwable) {
                                XposedBridge.log("LocationCallback spoofing error: $e")
                            }
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("LocationCallback hook error: $e")
        }

        // --- ANDROID 13/14: getCurrentLocation & registerGnssStatusCallback ---
        try {
            // getCurrentLocation
            XposedHelpers.findAndHookMethod(
                "android.location.LocationManager",
                lpparam?.classLoader,
                "getCurrentLocation",
                String::class.java,
                "android.os.CancellationSignal",
                "java.util.concurrent.Executor",
                "android.location.Consumer",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (settings.isStarted) {
                            val consumer = param.args.getOrNull(3)
                            val fakeLocation = getRealisticLocation(newlat, newlng)
                            // Call the callback directly with the fake location
                            XposedHelpers.callMethod(consumer, "accept", fakeLocation)
                            param.result = null // Prevent original execution
                        }
                    }
                }
            )

        } catch (e: Throwable) {
            XposedBridge.log("Android 13/14 location API hook error: $e")
        }

        // --- ENHANCED BROADCAST RECEIVER WITH NOTIFICATION ---
        try {
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                    if (intent?.action == "com.hamham.gpsmover.UPDATE_LOCATION") {
                        if (context == null) {
                            Timber.tag("GPS Mover").e("BroadcastReceiver context is null!")
                            return
                        }
                        newlat = intent.getDoubleExtra("lat", newlat)
                        newlng = intent.getDoubleExtra("lng", newlng)
                        // Show notification to user
                        try {
                            val channelId = "gps_mover_channel"
                            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                            if (notificationManager != null && android.os.Build.VERSION.SDK_INT >= 26) {
                                val channel = android.app.NotificationChannel(channelId, "GPS Mover", android.app.NotificationManager.IMPORTANCE_DEFAULT)
                                notificationManager.createNotificationChannel(channel)
                            }
                            val builder = android.app.Notification.Builder(context, channelId)
                                .setContentTitle("Location spoofed updated")
                                .setContentText("New coordinates: $newlat, $newlng")
                                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                                .setAutoCancel(true)
                            notificationManager?.notify(1001, builder.build())
                        } catch (e: Throwable) {
                            XposedBridge.log("Notification error: $e")
                        }
                    }
                }
            }
            context?.registerReceiver(receiver, android.content.IntentFilter("com.hamham.gpsmover.UPDATE_LOCATION"))
        } catch (e: Throwable) {
            XposedBridge.log("Dynamic update receiver error: $e")
        }


        XposedHelpers.findAndHookMethod(Location::class.java,"getLatitude", object : XC_MethodHook(){
            override fun beforeHookedMethod(param: MethodHookParam?) {
                super.beforeHookedMethod(param)
                ensureUpdatedLocation()
                if (settings.isStarted && !lpparam?.packageName.equals(BuildConfig.APPLICATION_ID)){
                    param?.result = newlat
                    Timber.tag("GPS Mover").d("getLatitude spoofed: $newlat for ${lpparam?.packageName}")
                }

            }
        })

        XposedHelpers.findAndHookMethod(Location::class.java,"getLongitude", object : XC_MethodHook(){
            override fun beforeHookedMethod(param: MethodHookParam?) {
                super.beforeHookedMethod(param)
                ensureUpdatedLocation()
                if (settings.isStarted && !lpparam?.packageName.equals(BuildConfig.APPLICATION_ID)){
                    param?.result = newlng
                    Timber.tag("GPS Mover").d("getLongitude spoofed: $newlng for ${lpparam?.packageName}")
                }


            }
        })

        XposedHelpers.findAndHookMethod(Location::class.java,"getAccuracy", object : XC_MethodHook(){
            override fun beforeHookedMethod(param: MethodHookParam?) {
                super.beforeHookedMethod(param)
                ensureUpdatedLocation()
                if (settings.isStarted && !lpparam?.packageName.equals(BuildConfig.APPLICATION_ID)){
                    param?.result = accuracy
                    Timber.tag("GPS Mover").d("getAccuracy spoofed: $accuracy for ${lpparam?.packageName}")
                }


            }
        })


        XposedHelpers.findAndHookMethod(Location::class.java, "set",
            Location::class.java, object : XC_MethodHook() {
                @RequiresApi(Build.VERSION_CODES.P)
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    ensureUpdatedLocation()
                    if (settings.isStarted && !lpparam?.packageName.equals(BuildConfig.APPLICATION_ID)){
                        // Null safety for param.args
                        val originArg = param.args.getOrNull(0)
                        val location = if (originArg == null) {
                            Location(LocationManager.GPS_PROVIDER).apply {
                                time = System.currentTimeMillis() - (100..10000).random()
                            }
                        } else {
                            val originLocation = originArg as? Location
                            if (originLocation == null) {
                                Timber.tag("GPS Mover").e("param.args[0] is not a Location!")
                                return
                            }
                            Location(originLocation.provider).apply {
                                time = originLocation.time
                                accuracy = accuracy
                                bearing = originLocation.bearing
                                bearingAccuracyDegrees = originLocation.bearingAccuracyDegrees
                                elapsedRealtimeNanos = originLocation.elapsedRealtimeNanos
                                verticalAccuracyMeters = originLocation.verticalAccuracyMeters
                            }
                        }
                        location.latitude = newlat
                        location.longitude = newlng
                        location.altitude = 0.0
                        location.speed = 0F
                        location.speedAccuracyMetersPerSecond = 0F
                        XposedBridge.log("GS: lat: ${location.latitude}, lon: ${location.longitude}")
                        try {
                            HiddenApiBypass.invoke(location.javaClass, location, "setIsFromMockProvider", false)
                        } catch (e: Exception) {
                            XposedBridge.log("GS: Not possible to mock (Pre Q)! $e")
                        }
                        param.args[0] = location
                        Timber.tag("GPS Mover").d("set(Location) spoofed for ${lpparam?.packageName}")

                    }
                }

            })


    }

    private fun setupSelfHooks(classLoader: ClassLoader){
        XposedHelpers.findAndHookMethod("com.hamham.gpsmover.xposed.XposedSelfHooks", classLoader, "isXposedModuleEnabled", object: XC_MethodReplacement(){
            override fun replaceHookedMethod(param: MethodHookParam): Any {
                param.result = true
                return true
            }
        })
        XposedHelpers.findAndHookMethod("com.hamham.gpsmover.xposed.XposedSelfHooks", classLoader, "getXSharedPrefsPath", object: XC_MethodReplacement(){
            override fun replaceHookedMethod(param: MethodHookParam): Any {
                val path = XSharedPreferences(BuildConfig.APPLICATION_ID, SHARED_PREFS_FILENAME).file.absolutePath
                param.result = path
                return path
            }
        })
    }

    /**
     * Update the current spoofed location and accuracy.
     * Ensures accuracy is not null before converting to float to avoid crashes.
     */
    private fun updateLocation() {
        try {
            mLastUpdated = System.currentTimeMillis()
            val range = settings.randomPositionRange
            val x: Double
            val y: Double
            if (settings.isRandomPositionEnabled) {
                x = (rand.nextInt((range * 2).toInt()) - range.toInt()).toDouble()
                y = (rand.nextInt((range * 2).toInt()) - range.toInt()).toDouble()
            } else {
                x = 0.0
                y = 0.0
            }
            val dlat = x / earth
            val dlng = y / (earth * cos(pi * settings.getLat / 180.0))
            newlat = settings.getLat + (dlat * 180.0 / pi)
            newlng = settings.getLng + (dlng * 180.0 / pi)
            // Do not set accuracy here; it will be set in getRealisticLocation only if spoofing is enabled
        }catch (e: Exception) {
            Timber.tag("GPS Mover").e(e, "Failed to get XposedSettings for %s", context.packageName)
        }
    }

    // --- IMPROVED GPS SPOOFING (DRIFT, ALTITUDE, BEARING, SPEED, TIME) ---
    /**
     * Generate a realistic spoofed Location with small, smooth drift for stability.
     * Drift is now much smaller to simulate a more stable, believable fake location.
     * If accuracy spoofing is disabled, do not set the accuracy field (so the real device value is used).
     */
    fun getRealisticLocation(lat: Double, lng: Double): Location {
        val location = Location(LocationManager.GPS_PROVIDER)
        // Generate a smaller, smoother drift for more realistic and stable movement
        val driftStep = 0.00001 // much smaller drift step for subtle movement
        val driftLat = lat + (rand.nextDouble() * (driftStep * 2) - driftStep)
        val driftLng = lng + (rand.nextDouble() * (driftStep * 2) - driftStep)
        location.latitude = driftLat
        location.longitude = driftLng
        // Realistic altitude (random between 10-300m)
        location.altitude = rand.nextInt(291) + 10.0 // 10 to 300 inclusive
        // Realistic bearing (0-360)
        location.bearing = (rand.nextInt(361)).toFloat() // 0 to 360 inclusive
        // Realistic speed (simulate walking/driving)
        location.speed = (rand.nextInt(10) + 1).toFloat() // 1 to 10 inclusive
        // Set accuracy only if enabled; otherwise, do not set it (let the real device value be used)
        if (settings.isAccuracyEnabled) {
            location.accuracy = settings.accuracyValue
        }
        // Realistic time (close to now, with small random offset)
        location.time = System.currentTimeMillis() - (rand.nextInt(1501) + 500) // 500 to 2000 ms
        return location
    }

    /**
     * Log debug messages only in debug builds to reduce log spam in production.
     */
    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Timber.tag("GPS Mover").d(message)
        }
    }

}
package com.hamham.gpsmover.xposed

object XposedSelfHooks {

    fun isXposedModuleEnabled(): Boolean {
        return false
    }

    fun getXSharedPrefsPath(): String {
        return "/data/data/com.hamham.gpsmover/shared_prefs/com.hamham.gpsmover_prefs.xml"
    }

}
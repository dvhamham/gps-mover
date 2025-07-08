package com.hamham.gpsmover.selfhook

object XposedSelfHooks {

    fun isXposedModuleEnabled(): Boolean {
        return false
    }

    fun getXSharedPrefsPath(): String {
        return ""
    }

}
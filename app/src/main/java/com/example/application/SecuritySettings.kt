package com.example.application

import android.content.Context
import android.content.SharedPreferences

object SecuritySettings {
    private const val PREF_NAME = "secure_prefs"
    private const val KEY_HIGH_SECURITY = "high_security_enabled"
    private const val KEY_APP_LOCK = "app_lock_enabled"
    private const val KEY_DEVICE_LOCK = "device_lock_enabled"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isHighSecurityEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_HIGH_SECURITY, false)
    }

    fun setHighSecurityEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIGH_SECURITY, enabled).apply()
    }

    fun isAppLockEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_APP_LOCK, false)
    }

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_APP_LOCK, enabled).commit()
    }

    fun isDeviceLockEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_DEVICE_LOCK, false)
    }

    fun setDeviceLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEVICE_LOCK, enabled).commit()
    }
}

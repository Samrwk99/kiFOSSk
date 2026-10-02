package com.shinydiscoballsdev.kifossk

import android.content.Context
import android.content.SharedPreferences

object KioskPrefs {
    private const val PREFS_NAME = "sillytavern_prefs"
    private const val DEFAULT_URL = "http://localhost:8000"

    private var prefsInstance: SharedPreferences? = null

    fun getInstance(context: Context): SharedPreferences {
        if (prefsInstance == null) {
            prefsInstance = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return prefsInstance!!
    }

    fun getUrl(context: Context): String {
        val prefs = getInstance(context)
        val url = prefs.getString("web_url", null)
        return UrlValidator.sanitizeOrDefault(url, DEFAULT_URL)
    }

    fun setUrl(context: Context, url: String) {
        getInstance(context).edit().putString("web_url", url).apply()
    }

    fun isFirstRun(context: Context): Boolean {
        return getInstance(context).getBoolean("first_run", true)
    }

    fun setFirstRun(context: Context, value: Boolean) {
        getInstance(context).edit().putBoolean("first_run", value).apply()
    }

    fun getOrientation(context: Context): String {
        return getInstance(context).getString("orientation", "auto") ?: "auto"
    }

    fun setOrientation(context: Context, orientation: String) {
        getInstance(context).edit().putString("orientation", orientation).apply()
    }

    fun getScreenOn(context: Context): Boolean {
        return getInstance(context).getBoolean("screen_on", false)
    }

    fun setScreenOn(context: Context, value: Boolean) {
        getInstance(context).edit().putBoolean("screen_on", value).apply()
    }

    fun getTheme(context: Context): String {
        return getInstance(context).getString("app_theme", "dark") ?: "dark"
    }

    fun setTheme(context: Context, theme: String) {
        getInstance(context).edit().putString("app_theme", theme).apply()
    }

    fun getKeepAlive(context: Context): Boolean {
        return getInstance(context).getBoolean("keep_alive", true)
    }

    fun setKeepAlive(context: Context, value: Boolean) {
        getInstance(context).edit().putBoolean("keep_alive", value).apply()
    }

    fun getShowNotification(context: Context): Boolean {
        return getInstance(context).getBoolean("show_notification", true)
    }

    fun setShowNotification(context: Context, value: Boolean) {
        getInstance(context).edit().putBoolean("show_notification", value).apply()
    }
}
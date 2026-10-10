package com.shinydiscoballsdev.kifossk

import android.content.Context
import android.content.SharedPreferences

/** Centralised, application-context-backed preferences. */
object KioskPrefs {
    private const val PREFS_NAME = "sillytavern_prefs"
    private const val DEFAULT_URL = "http://localhost:8000"

    @Volatile
    private var prefsInstance: SharedPreferences? = null

    fun getInstance(context: Context): SharedPreferences {
        return prefsInstance ?: synchronized(this) {
            prefsInstance ?: context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .also { prefsInstance = it }
        }
    }

    fun getUrl(context: Context): String {
        val saved = getInstance(context).getString("web_url", null)
        return UrlValidator.sanitizeOrDefault(saved, DEFAULT_URL)
    }

    fun setUrl(context: Context, url: String) {
        getInstance(context).edit().putString("web_url", url.trim()).apply()
    }

    fun isFirstRun(context: Context): Boolean =
        getInstance(context).getBoolean("first_run", true)

    fun setFirstRun(context: Context, value: Boolean) {
        getInstance(context).edit().putBoolean("first_run", value).apply()
    }

    fun getOrientation(context: Context): String =
        getInstance(context).getString("orientation", "auto")
            ?.takeIf { it in setOf("landscape", "portrait", "auto") } ?: "auto"

    fun setOrientation(context: Context, orientation: String) {
        val safe = orientation.takeIf { it in setOf("landscape", "portrait", "auto") } ?: "auto"
        getInstance(context).edit().putString("orientation", safe).apply()
    }

    fun getScreenOn(context: Context): Boolean =
        getInstance(context).getBoolean("screen_on", false)

    fun setScreenOn(context: Context, value: Boolean) {
        getInstance(context).edit().putBoolean("screen_on", value).apply()
    }

    fun getTheme(context: Context): String =
        getInstance(context).getString("app_theme", "dark")
            ?.takeIf { it == "dark" || it == "light" } ?: "dark"

    fun setTheme(context: Context, theme: String) {
        val safe = theme.takeIf { it == "dark" || it == "light" } ?: "dark"
        getInstance(context).edit().putString("app_theme", safe).apply()
    }

    /** Controls whether the ongoing notification includes descriptive text; it cannot remove an FGS notification. */
    fun getKeepAlive(context: Context): Boolean =
        getInstance(context).getBoolean("keep_alive", true)

    fun setKeepAlive(context: Context, value: Boolean) {
        getInstance(context).edit().putBoolean("keep_alive", value).apply()
    }

    fun getShowNotification(context: Context): Boolean =
        getInstance(context).getBoolean("show_notification", true)

    fun setShowNotification(context: Context, value: Boolean) {
        getInstance(context).edit().putBoolean("show_notification", value).apply()
    }
}

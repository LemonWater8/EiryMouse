package com.example.diazymouse.settings.store

import android.content.Context
import android.util.Log

object FloatingSettingsStore {
    private const val PREFS = "floating_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SCHEMA_VERSION = "schema_version"
    private const val CURRENT_SCHEMA_VERSION = 2
    private const val TAG = "FLOATING_TRACE"
    private const val KEY_STOWAGE_TIME_SECONDS = "stowage_time_seconds"
    private const val KEY_LOCKED = "locked"
    const val MIN_STOWAGE_TIME_SECONDS = 5
    const val MAX_STOWAGE_TIME_SECONDS = 30
    const val DEFAULT_STOWAGE_TIME_SECONDS = 5

    /**
     * One-time migration for builds where an old persisted false could hide
     * expanded state forever after an update. On the first launch of schema v2,
     * Floating is restored to ON. After this migration, explicit Config OFF
     * remains persistent normally.
     */
    fun ensureCurrentDefaults(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = prefs.getInt(KEY_SCHEMA_VERSION, 0)
        if (version >= CURRENT_SCHEMA_VERSION) return

        val ok = prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .commit()
        Log.i(TAG, "floating settings migrated -> enabled=true committed=$ok")
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun getStowageTimeSeconds(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_STOWAGE_TIME_SECONDS, DEFAULT_STOWAGE_TIME_SECONDS)
            .coerceIn(MIN_STOWAGE_TIME_SECONDS, MAX_STOWAGE_TIME_SECONDS)

    fun setStowageTimeSeconds(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_STOWAGE_TIME_SECONDS, seconds.coerceIn(MIN_STOWAGE_TIME_SECONDS, MAX_STOWAGE_TIME_SECONDS))
            .apply()
    }

    fun isLocked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOCKED, false)

    fun setLocked(context: Context, locked: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOCKED, locked)
            .apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .apply()
    }
}

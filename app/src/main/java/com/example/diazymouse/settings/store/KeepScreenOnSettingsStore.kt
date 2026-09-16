package com.example.diazymouse.settings.store

import android.content.Context

/**
 * Stores whether DiazyMouse should keep the display awake while the app is visible.
 * Default is OFF so Android's normal screen-timeout behavior is preserved.
 */
object KeepScreenOnSettingsStore {
    private const val PREFS = "keep_screen_on_settings"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}

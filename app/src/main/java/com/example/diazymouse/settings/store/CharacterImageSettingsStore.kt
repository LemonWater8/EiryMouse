package com.example.diazymouse.settings.store

import android.content.Context

object CharacterImageSettingsStore {
    private const val PREFS = "classic_character_image"
    private const val KEY_ENABLED = "enabled"
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}

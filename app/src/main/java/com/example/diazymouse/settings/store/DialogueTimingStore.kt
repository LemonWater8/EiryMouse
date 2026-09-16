package com.example.diazymouse.settings.store

import android.content.Context

object DialogueTimingStore {

    private const val PREFS_NAME = "dialogue_timing_settings"
    private const val KEY_FUN_MINUTES = "fun_minutes"
    private const val KEY_SAD_MINUTES = "sad_minutes"

    const val DEFAULT_FUN_MINUTES = 3
    const val DEFAULT_SAD_MINUTES = 5
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 60

    fun getFunMinutes(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_FUN_MINUTES, DEFAULT_FUN_MINUTES)
            .coerceIn(MIN_MINUTES, MAX_MINUTES)

    fun getSadMinutes(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_SAD_MINUTES, DEFAULT_SAD_MINUTES)
            .coerceIn(MIN_MINUTES, MAX_MINUTES)

    fun save(context: Context, funMinutes: Int, sadMinutes: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FUN_MINUTES, funMinutes.coerceIn(MIN_MINUTES, MAX_MINUTES))
            .putInt(KEY_SAD_MINUTES, sadMinutes.coerceIn(MIN_MINUTES, MAX_MINUTES))
            .apply()
    }

    fun funThresholdMs(context: Context): Long =
        getFunMinutes(context) * 60_000L

    fun sadThresholdMs(context: Context): Long =
        getSadMinutes(context) * 60_000L
}

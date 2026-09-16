package com.example.diazymouse.settings.store

import android.content.Context
import com.example.diazymouse.settings.model.OperationMode

object OperationModeSettingsStore {
    private const val PREFS = "operation_mode_settings"
    private const val KEY_MODE = "operation_mode"

    // Requested default: right-side AllTouch mode.
    fun get(context: Context): OperationMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, null)
        return runCatching { raw?.let(OperationMode::valueOf) }
            .getOrNull()
            ?: OperationMode.ALL_TOUCH
    }

    fun set(context: Context, mode: OperationMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }
}

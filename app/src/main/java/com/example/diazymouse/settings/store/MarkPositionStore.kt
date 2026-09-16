package com.example.diazymouse.settings.store

import android.content.Context
import android.util.Log
import com.example.diazymouse.settings.model.OperationMode

data class MarkPosition(val xRatio: Float, val yRatio: Float)

enum class MarkPositionSlot { UI_EXPANDED, STORED_COLLAPSED }

/**
 * Floating mark positions are independent for both operation mode and state.
 * A = UI_EXPANDED, B = STORED_COLLAPSED. Missing/corrupt data falls back to center.
 */
object MarkPositionStore {
    private const val KEY_X_RATIO = "x_ratio"
    private const val KEY_Y_RATIO = "y_ratio"
    private const val TAG = "FLOATING_TRACE"
    const val CENTER_RATIO = 0.5f

    private fun prefsName(context: Context, slot: MarkPositionSlot): String {
        val mode = when (OperationModeSettingsStore.get(context)) {
            OperationMode.CLASSIC -> "classic"
            OperationMode.ALL_TOUCH -> "alltouch"
        }
        val state = when (slot) {
            MarkPositionSlot.UI_EXPANDED -> "a_ui"
            MarkPositionSlot.STORED_COLLAPSED -> "b_stored"
        }
        return "floating_mark_position_${mode}_${state}"
    }

    fun load(context: Context, slot: MarkPositionSlot): MarkPosition? = runCatching {
        val prefs = context.getSharedPreferences(prefsName(context, slot), Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_X_RATIO) || !prefs.contains(KEY_Y_RATIO)) return null
        val x = prefs.getFloat(KEY_X_RATIO, Float.NaN)
        val y = prefs.getFloat(KEY_Y_RATIO, Float.NaN)
        if (!x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) {
            clear(context, slot)
            return null
        }
        MarkPosition(x, y)
    }.getOrElse {
        Log.w(TAG, "saved mark position unreadable", it)
        null
    }

    fun loadOrCenter(context: Context, slot: MarkPositionSlot): MarkPosition =
        load(context, slot) ?: MarkPosition(CENTER_RATIO, CENTER_RATIO)

    fun save(context: Context, slot: MarkPositionSlot, xRatio: Float, yRatio: Float): Boolean = runCatching {
        context.getSharedPreferences(prefsName(context, slot), Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_X_RATIO, xRatio.coerceIn(0f, 1f))
            .putFloat(KEY_Y_RATIO, yRatio.coerceIn(0f, 1f))
            .commit()
    }.getOrElse {
        Log.e(TAG, "failed to save mark position", it)
        false
    }

    fun clear(context: Context, slot: MarkPositionSlot) {
        context.getSharedPreferences(prefsName(context, slot), Context.MODE_PRIVATE).edit().clear().commit()
    }
}

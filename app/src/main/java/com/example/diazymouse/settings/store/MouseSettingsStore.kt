package com.example.diazymouse.settings.store

import android.content.Context

/**
 * Persistent settings for mouse movement and scroll-ring output.
 *
 * Defaults preserve the current v11.0 behavior:
 * - Mouse speed = 100 %
 * - Scroll ring amount = 1
 */
object MouseSettingsStore {

    private const val PREFS_NAME =
        "mouse_settings"

    private const val KEY_MOUSE_SPEED_PERCENT =
        "mouse_speed_percent"

    private const val KEY_SCROLL_RING_AMOUNT =
        "scroll_ring_amount"

    const val DEFAULT_MOUSE_SPEED_PERCENT = 100
    const val MIN_MOUSE_SPEED_PERCENT = 10
    const val MAX_MOUSE_SPEED_PERCENT = 300
    const val MOUSE_SPEED_STEP_PERCENT = 10

    const val DEFAULT_SCROLL_RING_AMOUNT = 1
    const val MIN_SCROLL_RING_AMOUNT = 1
    const val MAX_SCROLL_RING_AMOUNT = 10
    const val SCROLL_RING_STEP = 1

    fun getMouseSpeedPercent(
        context: Context
    ): Int =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).getInt(
            KEY_MOUSE_SPEED_PERCENT,
            DEFAULT_MOUSE_SPEED_PERCENT
        ).coerceIn(
            MIN_MOUSE_SPEED_PERCENT,
            MAX_MOUSE_SPEED_PERCENT
        )

    fun setMouseSpeedPercent(
        context: Context,
        value: Int
    ) {
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putInt(
                KEY_MOUSE_SPEED_PERCENT,
                value.coerceIn(
                    MIN_MOUSE_SPEED_PERCENT,
                    MAX_MOUSE_SPEED_PERCENT
                )
            )
            .apply()
    }

    fun getScrollRingAmount(
        context: Context
    ): Int =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).getInt(
            KEY_SCROLL_RING_AMOUNT,
            DEFAULT_SCROLL_RING_AMOUNT
        ).coerceIn(
            MIN_SCROLL_RING_AMOUNT,
            MAX_SCROLL_RING_AMOUNT
        )

    fun setScrollRingAmount(
        context: Context,
        value: Int
    ) {
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putInt(
                KEY_SCROLL_RING_AMOUNT,
                value.coerceIn(
                    MIN_SCROLL_RING_AMOUNT,
                    MAX_SCROLL_RING_AMOUNT
                )
            )
            .apply()
    }
}

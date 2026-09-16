package com.example.diazymouse.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.example.diazymouse.settings.store.VibrationSettingsStore
import com.example.diazymouse.settings.vibration.UiVibrationConfig

/**
 * Common vibration executor.
 *
 * Master ON/OFF and each saved vibration value are loaded from the device.
 */
class UiVibrator(
    context: Context
) {

    companion object {

        private const val PREFS_NAME =
            "ui_vibration_settings"

        private const val KEY_ENABLED =
            "vibration_enabled"

        fun isEnabled(
            context: Context
        ): Boolean =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            ).getBoolean(
                KEY_ENABLED,
                true
            )

        fun setEnabled(
            context: Context,
            enabled: Boolean
        ) {

            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            ).edit()
                .putBoolean(
                    KEY_ENABLED,
                    enabled
                )
                .apply()
        }
    }

    private val appContext =
        context.applicationContext

    private val vibrator =
        appContext.getSystemService(
            Vibrator::class.java
        ) as Vibrator

    /**
     * The caller passes the default pattern.
     * Saved user values are resolved immediately before vibration.
     */
    fun vibrate(
        pattern: UiVibrationConfig.Pattern
    ) {

        if (
            !isEnabled(
                appContext
            )
        ) {
            return
        }

        val actualPattern =
            VibrationSettingsStore.getPattern(
                appContext,
                pattern
            )

        vibrateResolved(
            durationMs =
                actualPattern.durationMs,
            amplitude =
                actualPattern.amplitude
        )
    }

    /**
     * Used by the Config screen when a direct preview is needed.
     */
    fun vibrateDirect(
        durationMs: Long,
        amplitude: Int
    ) {

        if (
            !isEnabled(
                appContext
            )
        ) {
            return
        }

        vibrateResolved(
            durationMs =
                durationMs.coerceIn(
                    VibrationSettingsStore.MIN_DURATION_MS,
                    VibrationSettingsStore.MAX_DURATION_MS
                ),
            amplitude =
                amplitude.coerceIn(
                    VibrationSettingsStore.MIN_AMPLITUDE,
                    VibrationSettingsStore.MAX_AMPLITUDE
                )
        )
    }

    private fun vibrateResolved(
        durationMs: Long,
        amplitude: Int
    ) {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    durationMs,
                    amplitude
                )
            )

        } else {

            @Suppress("DEPRECATION")
            vibrator.vibrate(
                durationMs
            )
        }
    }
}

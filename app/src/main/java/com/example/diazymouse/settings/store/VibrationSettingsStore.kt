package com.example.diazymouse.settings.store

import android.content.Context
import com.example.diazymouse.settings.vibration.UiVibrationConfig

/**
 * Saves and loads user-selected vibration values.
 *
 * The values are stored in SharedPreferences on the Android device.
 * Normal Android Studio Run / update installation keeps these values.
 */
object VibrationSettingsStore {

    private const val PREFS_NAME =
        "ui_vibration_values"

    private const val DURATION_SUFFIX =
        "_duration_ms"

    private const val AMPLITUDE_SUFFIX =
        "_amplitude"

    const val MIN_DURATION_MS =
        1L

    const val MAX_DURATION_MS =
        1000L

    const val MIN_AMPLITUDE =
        1

    const val MAX_AMPLITUDE =
        255

    fun getPattern(
        context: Context,
        defaultPattern: UiVibrationConfig.Pattern
    ): UiVibrationConfig.Pattern {

        val prefs =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        val duration =
            prefs.getLong(
                defaultPattern.key +
                    DURATION_SUFFIX,
                defaultPattern.durationMs
            ).coerceIn(
                MIN_DURATION_MS,
                MAX_DURATION_MS
            )

        val amplitude =
            prefs.getInt(
                defaultPattern.key +
                    AMPLITUDE_SUFFIX,
                defaultPattern.amplitude
            ).coerceIn(
                MIN_AMPLITUDE,
                MAX_AMPLITUDE
            )

        return defaultPattern.copy(
            durationMs = duration,
            amplitude = amplitude
        )
    }

    fun savePattern(
        context: Context,
        defaultPattern: UiVibrationConfig.Pattern,
        durationMs: Long,
        amplitude: Int
    ) {

        val safeDuration =
            durationMs.coerceIn(
                MIN_DURATION_MS,
                MAX_DURATION_MS
            )

        val safeAmplitude =
            amplitude.coerceIn(
                MIN_AMPLITUDE,
                MAX_AMPLITUDE
            )

        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putLong(
                defaultPattern.key +
                    DURATION_SUFFIX,
                safeDuration
            )
            .putInt(
                defaultPattern.key +
                    AMPLITUDE_SUFFIX,
                safeAmplitude
            )
            .apply()
    }
}

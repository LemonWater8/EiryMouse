package com.example.diazymouse.settings.store

import android.content.Context

/**
 * Stores the master dialogue voice setting.
 *
 * This setting is ready for Android TextToSpeech integration.
 * Actual TTS speech is not executed by this class.
 */
object VoiceSettingsStore {

    private const val PREFS_NAME =
        "dialogue_voice_settings"

    private const val KEY_ENABLED =
        "voice_enabled"

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

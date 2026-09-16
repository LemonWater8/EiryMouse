package com.example.diazymouse.settings.store

import android.content.Context
import com.example.diazymouse.character.CharacterProfile

object LanguageSettingsStore {

    private const val PREFS_NAME = "dialogue_language_settings"
    private const val KEY_LANGUAGE = "language"

    enum class Language(val displayName: String) {
        JA("JA"),
        EN("EN")
    }

    fun get(context: Context): Language {
        val saved = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(KEY_LANGUAGE, Language.JA.name)

        return try {
            Language.valueOf(saved ?: Language.JA.name)
        } catch (_: IllegalArgumentException) {
            Language.JA
        }
    }

    fun set(context: Context, language: Language) {
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putString(KEY_LANGUAGE, language.name)
            .apply()
    }

    fun character(context: Context): CharacterProfile =
        when (get(context)) {
            Language.JA -> CharacterProfile.CHAR1
            Language.EN -> CharacterProfile.CHAR2
        }
}

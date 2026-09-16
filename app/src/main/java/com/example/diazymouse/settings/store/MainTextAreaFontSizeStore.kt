package com.example.diazymouse.settings.store

import android.content.Context
import com.example.diazymouse.ui.UiConfig

/**
 * Stores the main text area font size in SharedPreferences.
 */
object MainTextAreaFontSizeStore {

    private const val PREFS_NAME =
        "main_text_area_font_size"

    private const val KEY_SIZE_SP =
        "font_size_sp"

    const val MIN_SIZE_SP =
        8

    const val MAX_SIZE_SP =
        72

    fun getSizeSp(
        context: Context
    ): Int {

        return context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).getInt(
            KEY_SIZE_SP,
            UiConfig.TEXT_AREA_TEXT_SIZE_SP.toInt()
        ).coerceIn(
            MIN_SIZE_SP,
            MAX_SIZE_SP
        )
    }

    fun saveSizeSp(
        context: Context,
        sizeSp: Int
    ) {

        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putInt(
                KEY_SIZE_SP,
                sizeSp.coerceIn(
                    MIN_SIZE_SP,
                    MAX_SIZE_SP
                )
            )
            .apply()
    }
}

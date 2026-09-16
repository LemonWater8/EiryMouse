package com.example.diazymouse.settings.store

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView

/**
 * Text Area 1 font setting.
 *
 * Cycle:
 * Android Standard -> Cursive -> Android Standard
 */
object FontSettingsStore {

    private const val PREFS_NAME =
        "ui_font_settings"

    private const val KEY_FONT_MODE =
        "text_area_1_font_mode"

    enum class FontMode(
        val displayName: String
    ) {
        STANDARD(
            "Android Standard"
        ),

        CURSIVE(
            "Cursive"
        )
    }

    fun getMode(
        context: Context
    ): FontMode {

        val savedName =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            ).getString(
                KEY_FONT_MODE,
                FontMode.STANDARD.name
            )

        return try {
            FontMode.valueOf(
                savedName ?: FontMode.STANDARD.name
            )
        } catch (
            _: IllegalArgumentException
        ) {
            FontMode.STANDARD
        }
    }

    fun setMode(
        context: Context,
        mode: FontMode
    ) {

        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putString(
                KEY_FONT_MODE,
                mode.name
            )
            .apply()
    }

    fun cycle(
        context: Context
    ): FontMode {

        val next =
            when (
                getMode(
                    context
                )
            ) {
                FontMode.STANDARD ->
                    FontMode.CURSIVE

                FontMode.CURSIVE ->
                    FontMode.STANDARD
            }

        setMode(
            context,
            next
        )

        return next
    }

    fun applyTo(
        context: Context,
        textView: TextView
    ) {

        textView.typeface =
            when (
                getMode(
                    context
                )
            ) {
                FontMode.STANDARD ->
                    Typeface.DEFAULT

                FontMode.CURSIVE ->
                    Typeface.create(
                        "cursive",
                        Typeface.NORMAL
                    )
            }
    }
}

package com.example.diazymouse.settings.store

import android.content.Context
import android.graphics.Color
import com.example.diazymouse.ui.UiConfig

/**
 * Stores the main text area background/text colors on the Android device.
 *
 * Values are saved as separate Red / Blue / Green channels.
 */
object MainTextAreaColorSettingsStore {

    private const val PREFS_NAME =
        "main_text_area_color_settings"

    private const val KEY_BG_RED =
        "background_red"

    private const val KEY_BG_BLUE =
        "background_blue"

    private const val KEY_BG_GREEN =
        "background_green"

    private const val KEY_TEXT_RED =
        "text_red"

    private const val KEY_TEXT_BLUE =
        "text_blue"

    private const val KEY_TEXT_GREEN =
        "text_green"

    data class RbgChannels(
        val red: Int,
        val blue: Int,
        val green: Int
    ) {
        fun toColor():
            Int =
            Color.rgb(
                red.coerceIn(
                    0,
                    255
                ),
                green.coerceIn(
                    0,
                    255
                ),
                blue.coerceIn(
                    0,
                    255
                )
            )
    }

    private fun prefs(
        context: Context
    ) =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun getBackground(
        context: Context
    ): RbgChannels {

        val defaultColor =
            UiConfig.TEXT_AREA_BACKGROUND

        return RbgChannels(
            red =
                prefs(
                    context
                ).getInt(
                    KEY_BG_RED,
                    Color.red(
                        defaultColor
                    )
                ),
            blue =
                prefs(
                    context
                ).getInt(
                    KEY_BG_BLUE,
                    Color.blue(
                        defaultColor
                    )
                ),
            green =
                prefs(
                    context
                ).getInt(
                    KEY_BG_GREEN,
                    Color.green(
                        defaultColor
                    )
                )
        )
    }

    fun saveBackground(
        context: Context,
        value: RbgChannels
    ) {

        prefs(
            context
        ).edit()
            .putInt(
                KEY_BG_RED,
                value.red.coerceIn(
                    0,
                    255
                )
            )
            .putInt(
                KEY_BG_BLUE,
                value.blue.coerceIn(
                    0,
                    255
                )
            )
            .putInt(
                KEY_BG_GREEN,
                value.green.coerceIn(
                    0,
                    255
                )
            )
            .apply()
    }

    fun getText(
        context: Context
    ): RbgChannels {

        val defaultColor =
            UiConfig.DISPLAY_TEXT_COLOR

        return RbgChannels(
            red =
                prefs(
                    context
                ).getInt(
                    KEY_TEXT_RED,
                    Color.red(
                        defaultColor
                    )
                ),
            blue =
                prefs(
                    context
                ).getInt(
                    KEY_TEXT_BLUE,
                    Color.blue(
                        defaultColor
                    )
                ),
            green =
                prefs(
                    context
                ).getInt(
                    KEY_TEXT_GREEN,
                    Color.green(
                        defaultColor
                    )
                )
        )
    }

    fun saveText(
        context: Context,
        value: RbgChannels
    ) {

        prefs(
            context
        ).edit()
            .putInt(
                KEY_TEXT_RED,
                value.red.coerceIn(
                    0,
                    255
                )
            )
            .putInt(
                KEY_TEXT_BLUE,
                value.blue.coerceIn(
                    0,
                    255
                )
            )
            .putInt(
                KEY_TEXT_GREEN,
                value.green.coerceIn(
                    0,
                    255
                )
            )
            .apply()
    }
}

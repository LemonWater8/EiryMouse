package com.example.diazymouse.input

import android.content.Context
import com.example.diazymouse.settings.model.MouseSpeedConfig
import com.example.diazymouse.settings.store.MouseSettingsStore
import kotlin.math.roundToInt

class MouseSensitivity(
    private val context: Context
) {

    fun multiplier(distance: Float): Float {
        val baseMultiplier =
            when {
                distance < MouseSpeedConfig.DISTANCE_LEVEL_1 ->
                    MouseSpeedConfig.MULTIPLIER_LEVEL_1

                distance < MouseSpeedConfig.DISTANCE_LEVEL_2 ->
                    MouseSpeedConfig.MULTIPLIER_LEVEL_2

                distance < MouseSpeedConfig.DISTANCE_LEVEL_3 ->
                    MouseSpeedConfig.MULTIPLIER_LEVEL_3

                else ->
                    MouseSpeedConfig.MULTIPLIER_LEVEL_4
            }

        val userScale =
            MouseSettingsStore.getMouseSpeedPercent(context) /
                100f

        return baseMultiplier * userScale
    }

    fun apply(rawDelta: Float, multiplier: Float): Int {
        return (rawDelta * multiplier).roundToInt()
    }
}

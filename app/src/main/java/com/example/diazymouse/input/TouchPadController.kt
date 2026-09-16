package com.example.diazymouse.input

import android.annotation.SuppressLint
import com.example.diazymouse.settings.vibration.UiVibrationConfig
import com.example.diazymouse.ui.UiVibrator
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

class TouchPadController(
    private val sensitivity: MouseSensitivity,
    private val uiVibrator: UiVibrator,
    private val vibration: UiVibrationConfig.Pattern,
    private val onTouchDisplay: () -> Unit,
    private val onMove: (dx: Int, dy: Int) -> Unit
) {

    private var lastX = 0f
    private var lastY = 0f

    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        view: View
    ) {

        view.setOnTouchListener { touchedView, event ->

            when (
                event.actionMasked
            ) {

                MotionEvent.ACTION_DOWN -> {

                    /*
                     * Touch-pad-specific vibration.
                     * It is generated once when the finger first touches
                     * the touch pad.
                     */
                    uiVibrator.vibrate(
                        vibration
                    )

                    onTouchDisplay()

                    lastX =
                        event.x

                    lastY =
                        event.y

                    true
                }

                MotionEvent.ACTION_MOVE -> {

                    val rawDx =
                        event.x - lastX

                    val rawDy =
                        event.y - lastY

                    lastX =
                        event.x

                    lastY =
                        event.y

                    val distance =
                        sqrt(
                            rawDx * rawDx +
                                rawDy * rawDy
                        )

                    val multiplier =
                        sensitivity.multiplier(
                            distance
                        )

                    val dx =
                        sensitivity.apply(
                            rawDx,
                            multiplier
                        )

                    val dy =
                        sensitivity.apply(
                            rawDy,
                            multiplier
                        )

                    if (
                        dx != 0 ||
                        dy != 0
                    ) {
                        onMove(
                            dx,
                            dy
                        )
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {

                    touchedView.performClick()

                    true
                }

                MotionEvent.ACTION_CANCEL ->
                    true

                else ->
                    false
            }
        }
    }
}

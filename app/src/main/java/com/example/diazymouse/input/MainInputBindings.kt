package com.example.diazymouse.input

import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.example.diazymouse.connection.MainInputTransport
import com.example.diazymouse.settings.vibration.UiVibrationConfig
import com.example.diazymouse.ui.UiConfig
import com.example.diazymouse.ui.UiDimensions
import com.example.diazymouse.ui.UiVibrator

/**
 * Connects already-created input views to their controllers and commands.
 *
 * This class contains input behavior only; view geometry is handled by
 * MainInputViews.
 */
class MainInputBindings(
    private val activity: ComponentActivity,
    private val inputTransport: MainInputTransport,
    private val dimensions: UiDimensions,
    private val onActionDisplay: (String) -> Unit
) {

    private val uiVibrator =
        UiVibrator(
            activity
        )

    fun attachScrollRing(
        scrollArea: FrameLayout
    ) {

        val controller =
            ScrollRingController(
                uiVibrator =
                    uiVibrator,
                touchVibration =
                    UiVibrationConfig.SCROLL_RING_TOUCH,
                stepVibration =
                    UiVibrationConfig.SCROLL_RING_STEP,
                onTouchDisplay = {

                    onActionDisplay(
                        "Scroll Ring"
                    )
                },
                onScroll = {
                    amount ->

                    inputTransport.scroll(amount)
                }
            )

        controller.attach(
            scrollArea =
                scrollArea,
            scrollAreaHeightPx =
                dimensions.scrollAreaHeightPx
        )
    }

    fun attachTouchPad(
        touchPad: FrameLayout
    ) {

        val controller =
            TouchPadController(
                sensitivity =
                    MouseSensitivity(activity),
                uiVibrator =
                    uiVibrator,
                vibration =
                    UiVibrationConfig.TOUCH_PAD,
                onTouchDisplay = {

                    onActionDisplay(
                        "Touch Pad"
                    )
                },
                onMove = {
                    dx,
                    dy ->

                    inputTransport.move(dx, dy)
                }
            )

        controller.attach(
            touchPad
        )
    }
}

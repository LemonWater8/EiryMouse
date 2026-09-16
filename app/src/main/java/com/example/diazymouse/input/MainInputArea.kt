package com.example.diazymouse.input

import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.example.diazymouse.connection.MainInputTransport
import com.example.diazymouse.ui.UiConfig
import com.example.diazymouse.ui.UiDimensions
import com.example.diazymouse.ui.UiVibrator

/**
 * Main input region coordinator.
 *
 * Responsibilities are intentionally small:
 * - ask MainInputViews to create views,
 * - ask MainInputBindings to attach behavior.
 */
class MainInputArea(
    activity: ComponentActivity,
    inputTransport: MainInputTransport,
    dimensions: UiDimensions,
    onActionDisplay: (String) -> Unit
) {

    private val views =
        MainInputViews(
            activity =
                activity,
            dimensions =
                dimensions
        )

    private val bindings =
        MainInputBindings(
            activity =
                activity,
            inputTransport =
                inputTransport,
            dimensions =
                dimensions,
            onActionDisplay =
                onActionDisplay
        )

    fun addTo(
        root: FrameLayout
    ): FrameLayout {

        val scrollArea =
            views.createScrollArea(
                root
            )

        val touchPad =
            views.createTouchPad(
                root
            )

        bindings.attachScrollRing(
            scrollArea
        )

        bindings.attachTouchPad(
            touchPad
        )

        return touchPad
    }
}

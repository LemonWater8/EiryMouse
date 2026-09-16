package com.example.diazymouse.ui.component

import android.view.Gravity
import com.example.diazymouse.connection.MainInputTransport
import com.example.diazymouse.connection.protocol.MouseCommand
import com.example.diazymouse.settings.vibration.UiVibrationConfig
import com.example.diazymouse.ui.UiConfig
import com.example.diazymouse.ui.UiDimensions
import com.example.diazymouse.ui.UiVibrator
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Creates the left/right click columns and the Back/Forward areas.
 *
 * Layout policy:
 * - Left click / right click keep their existing large columns.
 * - Left / Right Click remain visible down to the Touch Pad center.
 * - Back / Forward start at the Touch Pad center.
 * - Back / Forward extend down beside Text Area 1 to the screen bottom.
 * - Text Area 1 remains restricted to the center between Back / Forward.
 * - No forced bringToFront() or elevation is used.
 *
 * Back / Forward consume their complete touch sequence and execute once on
 * ACTION_UP. This prevents accidental duplicate input.
 */
class SideControlArea(
    private val activity: ComponentActivity,
    private val inputTransport: MainInputTransport,
    private val dimensions: UiDimensions,
    private val onActionDisplay: (String) -> Unit
) {

    private val uiVibrator =
        UiVibrator(
            activity
        )

    fun addTo(
        root: FrameLayout
    ) {

        addClickButtons(
            root
        )

        addNavigationAreas(
            root
        )
    }

    private fun addClickButtons(
        root: FrameLayout
    ) {

        val leftButton =
            createClickButton(
                text =
                    UiConfig.LEFT_CLICK_TEXT,
                command =
                    MouseCommand.LEFT_CLICK,
                vibration =
                    UiVibrationConfig.LEFT_CLICK,
                displayName =
                    "Left Click"
            )

        val rightButton =
            createClickButton(
                text =
                    UiConfig.RIGHT_CLICK_TEXT,
                command =
                    MouseCommand.RIGHT_CLICK,
                vibration =
                    UiVibrationConfig.RIGHT_CLICK,
                displayName =
                    "Right Click"
            )

        val clickAreaHeightPx =
            (
                dimensions.touchPadCenterPx -
                    dimensions.mainAreaTopPx
                ).coerceAtLeast(
                1
            )

        val leftParams =
            FrameLayout.LayoutParams(
                dimensions.buttonWidthPx,
                clickAreaHeightPx
            ).apply {

                gravity =
                    Gravity.TOP or
                        Gravity.START

                topMargin =
                    dimensions.mainAreaTopPx
            }

        val rightParams =
            FrameLayout.LayoutParams(
                dimensions.buttonWidthPx,
                clickAreaHeightPx
            ).apply {

                gravity =
                    Gravity.TOP or
                        Gravity.END

                topMargin =
                    dimensions.mainAreaTopPx
            }

        root.addView(
            leftButton,
            leftParams
        )

        root.addView(
            rightButton,
            rightParams
        )
    }

    private fun createClickButton(
        text: String,
        command: String,
        vibration: UiVibrationConfig.Pattern,
        displayName: String
    ): TextView =
        TextView(activity).apply {

            this.text =
                text

            textSize =
                UiConfig.CLICK_BUTTON_TEXT_SIZE_SP

            gravity =
                Gravity.CENTER

            setTextColor(
                UiConfig.TEXT_COLOR
            )

            setBackgroundColor(
                UiConfig.CLICK_BUTTON_BACKGROUND
            )

            isClickable =
                true

            setOnClickListener {

                uiVibrator.vibrate(
                    vibration
                )

                when (command) {
                    MouseCommand.LEFT_CLICK -> inputTransport.leftClick()
                    MouseCommand.RIGHT_CLICK -> inputTransport.rightClick()
                    else -> Unit
                }

                onActionDisplay(
                    displayName
                )
            }
        }

    private fun addNavigationAreas(
        root: FrameLayout
    ) {

        val backArea =
            createNavigationArea(
                command =
                    MouseCommand.BACK,
                vibration =
                    UiVibrationConfig.BACK,
                displayName =
                    "Back"
            )

        val forwardArea =
            createNavigationArea(
                command =
                    MouseCommand.FORWARD,
                vibration =
                    UiVibrationConfig.FORWARD,
                displayName =
                    "Forward"
            )

        val navigationHeightPx =
            dimensions.sideButtonHeightPx

        val backParams =
            FrameLayout.LayoutParams(
                dimensions.buttonWidthPx,
                navigationHeightPx
            ).apply {

                gravity =
                    Gravity.TOP or
                        Gravity.START

                /*
                 * Keep the original Back/Forward top edge.
                 * Only extend the bottom edge downward.
                 */
                topMargin =
                    dimensions.sideButtonTopMarginPx
            }

        val forwardParams =
            FrameLayout.LayoutParams(
                dimensions.buttonWidthPx,
                navigationHeightPx
            ).apply {

                gravity =
                    Gravity.TOP or
                        Gravity.END

                /*
                 * Same top edge as before.
                 * The lower edge extends to the bottom of the side area.
                 */
                topMargin =
                    dimensions.sideButtonTopMarginPx
            }

        /*
         * These are added after the click columns only to preserve the current
         * visible UI. No elevation is added.
         */
        root.addView(
            backArea,
            backParams
        )

        root.addView(
            forwardArea,
            forwardParams
        )
    }

    private fun createNavigationArea(
        command: String,
        vibration: UiVibrationConfig.Pattern,
        displayName: String
    ): FrameLayout =
        FrameLayout(activity).apply {

            setBackgroundColor(
                UiConfig.SIDE_NAVIGATION_BACKGROUND
            )

            isClickable =
                true

            isFocusable =
                true

            /*
             * Consume one complete gesture and send exactly one command.
             */
            setOnTouchListener {
                view,
                event ->

                when (
                    event.actionMasked
                ) {

                    MotionEvent.ACTION_DOWN -> {

                        view.isPressed =
                            true

                        true
                    }

                    MotionEvent.ACTION_MOVE -> {

                        true
                    }

                    MotionEvent.ACTION_UP -> {

                        view.isPressed =
                            false

                        uiVibrator.vibrate(
                            vibration
                        )

                        when (command) {
                            MouseCommand.BACK -> inputTransport.back()
                            MouseCommand.FORWARD -> inputTransport.forward()
                            else -> Unit
                        }

                        onActionDisplay(
                            displayName
                        )

                        view.performClick()

                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {

                        view.isPressed =
                            false

                        true
                    }

                    else ->
                        true
                }
            }

            /*
             * Required for accessibility because performClick() is called
             * from ACTION_UP. Command execution stays in the touch handler.
             */
            setOnClickListener {
            }
        }
}
